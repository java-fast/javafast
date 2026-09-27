package io.javafast.transport;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;

/**
 * Fixed-size block ring buffer for transport/request execution.
 * <p>
 * Each physical block is:
 * <p>
 * [ 4 byte state ][ payload ... ][ 4 byte next-index ]
 * <p>
 * A logical request may occupy multiple non-contiguous blocks.
 */
public final class TransportBuffer implements AutoCloseable {

    public static TransportBuffer create(int blockSize, int blockCount) {
        return new TransportBuffer(blockSize, blockCount);
    }

    private static boolean isNotPowerOfTwo(int n) {
        return n == 0 || (n & n - 1) != 0;
    }

    private static final int FREE = 0;
    private static final int IN_USE = 1;

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
    private final int nextBlockOffset;

    private final Block[] blocks;

    /*
     * Only for probing hints.
     * This writeCursor is not part of the ownership protocol.
     * Correctness still depends exclusively on the block state.
     */
    private volatile int writeCursor;

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
        this.nextBlockOffset = blockSize - NEXT_SIZE;

        final MemorySegment mainBuffer = this.arena.allocate((long) blockSize * blockCount, 8);
        this.blocks = new Block[blockCount];

        for (int i = 0; i < blockCount; i++) {
            final long offset = (long) i * blockSize;
            final MemorySegment segment = mainBuffer.asSlice(offset, blockSize);
            this.blocks[i] = new Block(i, segment);
            setStateFree(i);
            clearNext(i);
        }
    }

    public Block acquire() {
        ensureOpen();
        final int index = acquireFreeBlock();
        if (index < 0) {
            return null; // buffer exhausted
        }
        return this.blocks[index];
    }

    /**
     * Acquires one physical block.
     * Linear probing starts from the producer cursor.
     */
    private int acquireFreeBlock() {
        final int start = this.writeCursor;

        for (int i = 0; i < this.blockCount; i++) {
            final int index = (start + i) & this.blockCountMask;
            if (setStateInUse(index)) {
                this.writeCursor = (index + 1) & this.blockCountMask;
                return index;
            }
        }

        return -1;
    }

    private int getState(int index) {
        return (int) STATE_HANDLE.getAcquire(this.blocks[index].segment, STATE_OFFSET);
    }

    private void setStateFree(int index) {
        STATE_HANDLE.setRelease(this.blocks[index].segment, STATE_OFFSET, TransportBuffer.FREE);
    }

    private boolean setStateInUse(int index) {
        return (boolean) STATE_HANDLE.compareAndSet(this.blocks[index].segment, STATE_OFFSET, TransportBuffer.FREE, TransportBuffer.IN_USE);
    }

    private int getNext(int index) {
        return this.blocks[index].segment.get(ValueLayout.JAVA_INT_UNALIGNED, this.nextBlockOffset);
    }

    private void clearNext(int index) {
        this.blocks[index].segment.set(ValueLayout.JAVA_INT_UNALIGNED, this.blockSize - NEXT_SIZE, TransportBuffer.NEXT_NONE);
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
            if (getState(i) == FREE) {
                count++;
            }
        }
        return count;
    }

    private void ensureOpen() {
        if (this.closed) {
            throw new IllegalStateException("TransportBuffer is closed");
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
        private final ByteBuffer payload; // [payload]

        private Block(int index, MemorySegment segment) {
            this.index = index;
            this.segment = segment;
            this.payload = segment.asSlice(STATE_OFFSET + STATE_SIZE, segment.byteSize() - STATE_SIZE - NEXT_SIZE).asByteBuffer();
        }

        /**
         * Payload only. Excludes state bytes and next-index trailer.
         */
        public ByteBuffer payload() {
            return this.payload;
        }

        /**
         * Releases a previously acquired block chain.
         * No CAS is necessary: caller owns the entire chain
         */
        public void release() {
            int current = this.index;
            while (current != NEXT_NONE) {
                final int next = getNext(current);
                clearNext(current);
                setStateFree(current);
                blocks[current].payload.clear();
                current = next;
            }
        }

        public void link(Block next) {
            this.segment.set(ValueLayout.JAVA_INT_UNALIGNED, nextBlockOffset, next.index);
        }

        @Override
        public String toString() {
            return "Block[" + this.index + ']';
        }
    }

}
