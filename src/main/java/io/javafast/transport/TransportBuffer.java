package io.javafast.transport;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;

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

    public Writer writer() {
        final Block block = acquire();
        return block == null ? null : block.writer();
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

        private final Writer writer = new Writer();
        private final Reader reader = new Reader(this);

        private final int index;
        private final MemorySegment segment; // [state][payload][next]
        private final MemorySegment payload; // [payload]
        private final ByteBuffer byteBuffer; // ByteBuffer view of the payload

        private Block(int index, MemorySegment segment) {
            this.index = index;
            this.segment = segment;
            this.payload = segment.asSlice(STATE_OFFSET + STATE_SIZE, segment.byteSize() - STATE_SIZE - NEXT_SIZE);
            this.byteBuffer = this.payload.asByteBuffer();
            this.writer.head = this; // head never changes
        }

        private Writer writer() {
            // block-confined writer takeover
            if (this.writer.current == null) {
                this.writer.current = this;
            }
            return this.writer;
        }

        private Reader reader() {
            return this.reader;
        }

        /**
         * Payload only. Excludes state bytes and next-index trailer.
         */
        public MemorySegment payload() {
            return this.payload;
        }

        public ByteBuffer byteBuffer() {
            return this.byteBuffer;
        }

        /**
         * Releases a previously acquired block chain.
         * No CAS is necessary: caller owns the entire chain
         */
        public void release() {
            this.writer.reset();
            this.reader.reset();
            int current = this.index;
            while (current != NEXT_NONE) {
                blocks[current].byteBuffer.clear();
                final int next = getNext(current);
                clearNext(current);
                setStateFree(current);
                current = next;
            }
        }

        public void link(Block next) {
            this.segment.set(ValueLayout.JAVA_INT_UNALIGNED, nextBlockOffset, next.index);
        }

        public Block next() {
            final int next = getNext(this.index);
            if (next == NEXT_NONE) {
                return null;
            }
            return blocks[next];
        }

        @Override
        public String toString() {
            return "Block[" + this.index + ']';
        }
    }

    public final class Writer {

        private Block head;
        private Block current;
        private long totalWritten;

        private Writer() {
        }

        public int read(ReadableByteChannel channel) throws IOException {
            if (this.current == null) { // try acquiring the first block
                this.current = acquire();
                if (this.current == null) {
                    return 0;
                }
                this.head = this.current;
            }
            int read = 0;
            for (; ; ) {
                final ByteBuffer target = this.current.byteBuffer;
                final int written = channel.read(target);
                if (written < 0) {
                    return read == 0 ? -1 : read;
                }
                read += written;
                if (target.hasRemaining()) {
                    break;
                }
                final Block next = acquire();
                if (next == null) {
                    throw new BufferOverflowException();
                }
                this.current.link(next);
                this.current = next;
            }
            this.totalWritten += read;
            return read;
        }

        public void release() {
            this.head.release();
        }

        public void reset() {
            // block-confined writer reset
            this.current = null;
            this.totalWritten = 0;
        }

        public long totalWritten() {
            return this.totalWritten;
        }

        Block head() {
            return this.head;
        }

        Reader reader() {
            return this.head.reader();
        }

    }

    public final class Reader {

        private final Block head;
        private Block current;

        private int currentOffset;
        private long position;

        private Reader(Block head) {
            this.head = head;
            this.current = this.head;
        }

        void reset() {
            this.current = this.head;
            this.currentOffset = 0;
            this.position = 0;
        }

        void release() {
            this.head.release();
        }

        public long position() {
            return this.position;
        }

        public Reader position(long position) {
            if (position < 0) {
                throw new IllegalArgumentException("position < 0");
            }
            this.position = position;
            locate(position);
            return this;
        }

        public long limit() {
            return this.head.writer.totalWritten;
        }

        /**
         * Number of bytes available from the current position.
         */
        public long remaining() {
            return limit() - this.position;
        }

        public boolean asciiEquals(int offset, String value) {
            final int length = value.length();

            if (offset < 0 || (long) offset + length > limit()) {
                return false;
            }

            locate(offset);

            int remaining = length;
            int stringOffset = 0;

            Block block = this.current;
            int blockOffset = this.currentOffset;

            while (remaining > 0) {
                final ByteBuffer buffer = block.byteBuffer();
                final int available = buffer.position() - blockOffset;
                final int count = Math.min(available, remaining);

                for (int i = 0; i < count; i++) {
                    if ((buffer.get(blockOffset + i) & 0xff) != value.charAt(stringOffset + i)) {
                        return false;
                    }
                }

                remaining -= count;
                stringOffset += count;
                blockOffset += count;

                if (remaining > 0) {
                    block = block.next();
                    blockOffset = 0;
                    if (block == null) {
                        return false;
                    }
                }
            }

            return true;
        }

        public int indexOf(byte value, int offset) {
            if (offset < 0) {
                throw new IllegalArgumentException("offset < 0");
            }

            if (offset >= limit()) {
                return -1;
            }

            locate(offset);

            Block block = this.current;
            int blockOffset = this.currentOffset;
            while (block != null) {
                final ByteBuffer buffer = block.byteBuffer();
                final int end = buffer.position();
                for (int i = blockOffset; i < end; i++) {
                    if (buffer.get(i) == value) {
                        return logicalOffset(block, i);
                    }
                }
                block = block.next();
                blockOffset = 0;
            }

            return -1;
        }

        public int indexOf(short value, int offset) {
            if (offset < 0) {
                throw new IllegalArgumentException("offset < 0");
            }
            if (offset >= limit()) {
                return -1;
            }
            locate(offset);

            Block block = this.current;
            int blockOffset = this.currentOffset;
            while (block != null) {
                final ByteBuffer buffer = block.byteBuffer();
                final int end = buffer.position();
                for (int i = blockOffset; i < end; i++) {
                    if (buffer.getShort(i) == value) {
                        return logicalOffset(block, i);
                    }
                }
                block = block.next();
                blockOffset = 0;
            }

            return -1;
        }

        /**
         * Relative get
         */
        public byte get() {
            if (this.current == null) {
                throw new IndexOutOfBoundsException();
            }

            ByteBuffer buffer = this.current.byteBuffer();
            if (this.currentOffset >= buffer.position()) {
                this.current = this.current.next();
                this.currentOffset = 0;
                if (this.current == null) {
                    throw new IndexOutOfBoundsException();
                }
                buffer = this.current.byteBuffer();
            }

            final byte value = buffer.get(this.currentOffset++);
            this.position++;
            return value;
        }

        public byte[] getBytes(int offset, int length) {
            if (length < 0) {
                throw new IllegalArgumentException("length < 0");
            }
            final byte[] result = new byte[length];
            copyTo(offset, result, 0, length);
            return result;
        }

        public String getAscii(int offset, int length) {
            final byte[] bytes = getBytes(offset, length);
            return new String(bytes, StandardCharsets.US_ASCII);
        }

        public String getUtf8(int offset, int length) {
            final byte[] bytes = getBytes(offset, length);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        public void copyTo(int offset, byte[] destination, int destinationOffset, int length) {
            if (length < 0 || offset < 0 || destinationOffset < 0 || destinationOffset + length > destination.length) {
                throw new IndexOutOfBoundsException();
            }

            if ((long) offset + length > limit()) {
                throw new IndexOutOfBoundsException("Not enough data: offset=" + offset + ", length=" + length);
            }

            locate(offset);

            Block block = this.current;
            int blockOffset = this.currentOffset;
            int remaining = length;
            int dest = destinationOffset;
            while (block != null && remaining > 0) {
                final ByteBuffer buffer = block.byteBuffer();
                final int available = buffer.position() - blockOffset;
                final int count = Math.min(available, remaining);

                final ByteBuffer source = buffer.duplicate();
                source.position(blockOffset);
                source.limit(blockOffset + count);

                source.get(destination, dest, count);

                remaining -= count;
                dest += count;
                blockOffset += count;

                if (remaining > 0) {
                    block = block.next();
                    blockOffset = 0;
                }
            }
        }

        /**
         * Returns a zero-copy MemorySegment if the requested range resides entirely inside one block.
         * Returns null when the range crosses a block boundary.
         */
        public MemorySegment segment(int offset, int length) {
            if (length < 0 || offset < 0 || (long) offset + length > limit()) {
                throw new IndexOutOfBoundsException();
            }

            locate(offset);

            final ByteBuffer buffer = this.current.byteBuffer();
            if (this.currentOffset + length > buffer.position()) {
                return null;
            }
            return this.current.payload().asSlice(this.currentOffset, length);
        }

        private void locate(long target) {
            if (target == this.position && this.current != null) {
                return;
            }

            Block block = this.head;
            long logical = 0;
            while (block != null) {
                final int size = block.byteBuffer().position();
                if (target < logical + size) {
                    this.current = block;
                    this.currentOffset = (int) (target - logical);
                    this.position = target;
                    return;
                }
                logical += size;
                block = block.next();
            }

            if (target == logical) {
                this.current = null;
                this.currentOffset = 0;
                this.position = target;
                return;
            }

            throw new IndexOutOfBoundsException("Position outside of buffer: " + target);
        }

        private int logicalOffset(Block target, int offset) {
            int result = offset;
            Block block = this.head;
            while (block != target) {
                // target is always in the chain
                // so block cannot be null until target
                result += block.byteBuffer().position();
                block = block.next();
            }
            return result;
        }
    }
}
