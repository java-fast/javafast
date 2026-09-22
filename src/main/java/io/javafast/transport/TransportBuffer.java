package io.javafast.transport;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fixed-size block ring buffer for transport/request execution.
 * <p>
 * Each physical block is:
 * <p>
 * [ 4 byte state ][ payload ... ][ 4 byte next-index ]
 * <p>
 * A logical request may occupy multiple non-contiguous blocks.
 * <p>
 * Ownership protocol:
 * <p>
 * FREE
 * |
 * | CAS
 * v
 * WRITING
 * |
 * | release store
 * v
 * CLAIMABLE_HEAD / IN_USE (Continuations)
 * |
 * | HEAD CAS only
 * v
 * IN_USE
 * |
 * | release store
 * v
 * FREE
 * <p>
 * Only CLAIMABLE_HEAD is discoverable by consumers.
 * Once the head is claimed, the consumer owns the complete chain.
 * <p>
 * Sample block consume:
 * <pre>
 * Block block = claimHead();
 * while (block != null) {
 *     // consume block
 *     int next = block.next;
 *     release(block);
 *     if (next == -1) {
 *         break;
 *     }
 *     block = blocks[next];
 * }
 * </pre>
 */
public final class TransportBuffer implements AutoCloseable {

    public static TransportBuffer create(int blockSize, int blockCount) {
        return new TransportBuffer(blockSize, blockCount);
    }

    private static boolean isNotPowerOfTwo(int n) {
        return n == 0 || (n & n - 1) != 0;
    }

    private static final int FREE = 0;
    private static final int WRITING = 1;
    private static final int CLAIMABLE_HEAD = 2;
    private static final int IN_USE = 3;

    private static final int NEXT_NONE = -1;

    private static final int STATE_SIZE = Integer.BYTES;
    private static final int STATE_OFFSET = 0;
    private static final int NEXT_SIZE = Integer.BYTES;

    private static final VarHandle STATE_HANDLE = ValueLayout.JAVA_INT.varHandle();

    private final Arena arena;

    private final int blockSize;
    private final int payloadSize;
    private final int blockCount;
    private final int blockCountMask;

    private final Block[] blocks;

    /*
     * These are probing hints only.
     *
     * They are not part of the ownership protocol.
     * Correctness depends exclusively on block state.
     */
    private volatile int producerCursor;
    private volatile int consumerCursor;

    private volatile boolean closed;

    private TransportBuffer(int blockSize, int blockCount) {

        if (blockSize < 64) {
            throw new IllegalArgumentException("blockSize must be >= 64");
        }
        if (blockCount < 0 || isNotPowerOfTwo(blockCount)) {
            throw new IllegalArgumentException("blockCount must be > 0 and a power of two");
        }

        this.arena = Arena.ofShared();
        this.blockSize = blockSize;
        this.payloadSize = blockSize - STATE_OFFSET - STATE_SIZE - NEXT_SIZE;
        this.blockCount = blockCount;
        this.blockCountMask = blockCount - 1;

        final MemorySegment mainBuffer = this.arena.allocate((long) blockSize * blockCount, 8);
        this.blocks = new Block[blockCount];

        for (int i = 0; i < blockCount; i++) {
            final long offset = (long) i * blockSize;
            final MemorySegment segment = mainBuffer.asSlice(offset, blockSize);
            this.blocks[i] = new Block(i, segment);
            stateSetRelease(i, FREE);
            setNext(i, NEXT_NONE);
        }
    }

    /**
     * Copies a request into the buffer.
     * <p>
     * Blocks are acquired lazily.
     * <p>
     * Example:
     * <p>
     * request
     * |
     * +--> block 10
     * +--> block 91
     * +--> block 7
     * <p>
     * Physical adjacency is not required.
     */
    public boolean write(TransportSource source) {
        int first = -1;
        int tail = -1;
        try {
            while (true) {
                final int index = acquireFreeBlock();
                if (index < 0) {
                    // rollback head
                    releaseUnpublishedChain(first);
                    return false;
                }
                if (first == -1) {
                    first = index;
                } else {
                    setNext(tail, index);
                }
                tail = index;

                final MemorySegment destination = this.blocks[index].payload();
                long remaining = this.payloadSize;
                while (remaining > 0) {
                    final int written = source.read(destination.asSlice(this.payloadSize - remaining));
                    if (written == -1) {
                        publishChain(first);
                        return true;
                    }
                    if (written <= 0 || written > remaining) {
                        throw new IllegalStateException("Invalid TransportSource.read() result: " + written);
                    }
                    remaining -= written;
                }
                // Block is full.
                // Do NOT acquire the next block until we actually need it.
            }
        } catch (Throwable t) {
            // rollback head
            releaseUnpublishedChain(first);
            throw t;
        }
    }


    /**
     * Claims the HEAD of a complete logical request.
     * Continuation blocks can be traversed via block.next()
     * Only the HEAD participates in CAS.
     */
    public Block claim() {
        ensureOpen();
        final int start = this.consumerCursor;
        for (int i = 0; i < this.blockCount; i++) {
            final int index = (start + i) & this.blockCountMask;
            if (stateAcquire(index) == CLAIMABLE_HEAD
                /*
                 * This is the single ownership CAS.
                 * Once successful, the caller owns the entire
                 * chain reachable from this block.
                 */
                && stateCompareAndSet(index, CLAIMABLE_HEAD, IN_USE)) {
                this.consumerCursor = (index + 1) & this.blockCountMask;
                return this.blocks[index];
            }
        }
        return null;
    }

    /**
     * Releases a previously claimed request.
     * No CAS is necessary: caller owns the entire chain
     * therefore:
     * IN_USE -> FREE
     * is an unconditional release-store
     */
    public void release(Block claimed) {
        Block current = claimed;
        while (current != null) {
            /*
             * Clear the link before publishing FREE.
             * A producer that acquires FREE afterward will therefore never observe the old chain.
             */
            final Block next = current.next();
            setNext(current.index, NEXT_NONE);
            stateSetRelease(current.index, FREE);
            current = next;
        }
    }

    /**
     * Publishes the HEAD.
     * Only set HEAD since the continuation blocks can stay in WRITING state
     */
    private void publishChain(int first) {
        stateSetRelease(first, CLAIMABLE_HEAD);
    }

    /**
     * Acquires one physical block.
     * Linear probing starts from the producer cursor.
     */
    private int acquireFreeBlock() {
        final int start = this.producerCursor;

        for (int i = 0; i < this.blockCount; i++) {
            final int index = (start + i) & this.blockCountMask;
            if (stateCompareAndSet(index, FREE, WRITING)) {
                this.producerCursor = (index + 1) & this.blockCountMask;
                return index;
            }
        }

        return -1;
    }

    /**
     * Rolls back a partially constructed request.
     * <p>
     * The chain has never been published, so no CAS is needed.
     */
    private void releaseUnpublishedChain(int first) {
        int current = first;
        while (current != NEXT_NONE) {
            final int next = getNext(current);
            setNext(current, NEXT_NONE);
            stateSetRelease(current, FREE);
            current = next;
        }
    }

    private int stateAcquire(int index) {
        return (int) STATE_HANDLE.getAcquire(this.blocks[index].segment, STATE_OFFSET);
    }

    private void stateSetRelease(int index, int state) {
        STATE_HANDLE.setRelease(this.blocks[index].segment, STATE_OFFSET, state);
    }

    private boolean stateCompareAndSet(int index, int expected, int update) {
        return (boolean) STATE_HANDLE.compareAndSet(this.blocks[index].segment, STATE_OFFSET, expected, update);
    }

    private int getNext(int index) {
        return this.blocks[index].segment.get(ValueLayout.JAVA_INT_UNALIGNED, this.blockSize - NEXT_SIZE);
    }

    private void setNext(int index, int next) {
        this.blocks[index].segment.set(ValueLayout.JAVA_INT_UNALIGNED, this.blockSize - NEXT_SIZE, next);
    }

    /**
     * DEBUG methods
     */
    int capacity() {
        return this.payloadSize * this.blockCount;
    }

    int freeBlockCount() {
        int count = 0;
        for (int i = 0; i < this.blockCount; i++) {
            if (stateAcquire(i) == FREE) {
                count++;
            }
        }
        return count;
    }

    private void ensureOpen() {
        if (this.closed) {
            throw new IllegalStateException("Buffer is closed");
        }
    }

    @Override
    public void close() {
        this.closed = true;
        this.arena.close();
    }

    public final class Block {

        private final int index;
        private final MemorySegment segment; // [state][payload][next]
        private final MemorySegment payload; // [state][payload][next]

        private Block(int index, MemorySegment segment) {
            this.index = index;
            this.segment = segment;
            this.payload = segment.asSlice(STATE_OFFSET + STATE_SIZE, segment.byteSize() - STATE_SIZE - NEXT_SIZE);
        }

        /**
         * Payload only. Excludes state bytes and next-index trailer.
         */
        public MemorySegment payload() {
            return this.payload;
        }

        public Block next() {
            final int next = this.segment.get(ValueLayout.JAVA_INT_UNALIGNED, this.segment.byteSize() - NEXT_SIZE);
            if (next == NEXT_NONE) {
                return null;
            }
            return TransportBuffer.this.blocks[next];
        }

        public void consume(Consumer<MemorySegment> consumer) {
            Block current = this;
            while (true) {
                consumer.accept(current.payload()); // process block.payload()
                final Block next = current.next();
                if (next == null) {
                    break;
                }
                current = next;
            }
            release(this);
        }

        @Override
        public String toString() {
            return "Block[" + this.index + ']';
        }
    }

}
