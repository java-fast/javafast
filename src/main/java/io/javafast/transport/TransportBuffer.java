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

        Reader reader() {
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

        /**
         * Logical position within the request.
         * Request size is bounded by MAX_REQUEST_SIZE, therefore an int is enough.
         */
        private int totalWritten;

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

        public int totalWritten() {
            return this.totalWritten;
        }

        public Reader reader() {
            return this.head.reader();
        }

        Block head() {
            return this.head;
        }

    }

    public final class Reader {

        private final Block head;
        private Block current;

        private int currentOffset;

        /**
         * Logical position within the request.
         * Request size is bounded by MAX_REQUEST_SIZE, therefore an int is enough.
         */
        private int position;

        private Reader(Block head) {
            this.head = head;
            this.current = this.head;
        }

        void release() {
            this.head.release();
        }

        public int position() {
            return this.position;
        }

        public Reader position(int position) {
            locate(position);
            return this;
        }

        public int written(Block block) {
            return block.byteBuffer.position();
        }

        public int limit() {
            return this.head.writer.totalWritten;
        }

        public int remaining() {
            return limit() - this.position;
        }

        public boolean hasRemaining() {
            return this.position < limit();
        }

        // -------------------------------------------------------------------------
        // Cursor
        // -------------------------------------------------------------------------

        /**
         * Returns the next byte without advancing the cursor.
         */
        public byte peek() {
            ensureAvailable();
            return this.current.payload().get(ValueLayout.JAVA_BYTE, this.currentOffset);
        }

        /**
         * Returns the next byte and advances the cursor by one.
         */
        public byte get() {
            ensureAvailable();
            final byte value = this.current.payload().get(ValueLayout.JAVA_BYTE, this.currentOffset);
            this.currentOffset++;
            this.position++;
            return value;
        }

        public int getUnsignedByte() {
            return get() & 0xff;
        }

        /**
         * Reads a big-endian short and advances by two bytes.
         * Handles block boundaries.
         */
        public short getShort() {
            if (remaining() < 2) {
                throw new IndexOutOfBoundsException("Not enough data");
            }

            final MemorySegment segment = this.current.payload();
            if (this.currentOffset + 2 <= written(this.current)) {
                final short value = segment.get(ValueLayout.JAVA_SHORT_UNALIGNED, this.currentOffset);
                this.currentOffset += 2;
                this.position += 2;
                return value;
            }

            // Cross-block
            final int high = getUnsignedByte();
            final int low = getUnsignedByte();

            return (short) ((high << 8) | low);
        }

        /**
         * Reads a big-endian int and advances by four bytes.
         * Handles block boundaries.
         */
        public int getInt() {
            if (remaining() < 4) {
                throw new IndexOutOfBoundsException("Not enough data");
            }

            final MemorySegment segment = this.current.payload();
            if (this.currentOffset + 4 <= written(this.current)) {
                final int value = segment.get(ValueLayout.JAVA_INT_UNALIGNED, this.currentOffset);
                this.currentOffset += 4;
                this.position += 4;
                return value;
            }

            // Cross-block
            int value = getUnsignedByte();
            value = (value << 8) | getUnsignedByte();
            value = (value << 8) | getUnsignedByte();
            value = (value << 8) | getUnsignedByte();

            return value;
        }

        /**
         * Reads a big-endian long and advances by eight bytes.
         * Handles block boundaries.
         */
        public long getLong() {
            if (remaining() < 8) {
                throw new IndexOutOfBoundsException("Not enough data");
            }

            final MemorySegment segment = this.current.payload();
            if (this.currentOffset + 8 <= written(this.current)) {
                final long value = segment.get(ValueLayout.JAVA_LONG_UNALIGNED, this.currentOffset);
                this.currentOffset += 8;
                this.position += 8;
                return value;
            }

            // Cross-block
            long value = getUnsignedByte();
            value = (value << 8) | getUnsignedByte();
            value = (value << 8) | getUnsignedByte();
            value = (value << 8) | getUnsignedByte();
            value = (value << 8) | getUnsignedByte();
            value = (value << 8) | getUnsignedByte();
            value = (value << 8) | getUnsignedByte();
            value = (value << 8) | getUnsignedByte();

            return value;
        }

        /**
         * Advances the cursor by length bytes.
         */
        public Reader skip(int length) {
            if (length < 0) {
                throw new IllegalArgumentException("length < 0");
            }
            if ((long) length > remaining()) {
                throw new IndexOutOfBoundsException("Not enough data: length=" + length + ", remaining=" + remaining());
            }
            advance(length);
            return this;
        }

        /**
         * Moves the cursor to the beginning.
         */
        void reset() {
            this.current = this.head;
            this.currentOffset = 0;
            this.position = 0;
        }

        /**
         * Moves the cursor to the end.
         */
        public Reader skipRemaining() {
            this.current = null;
            this.currentOffset = 0;
            this.position = limit();
            return this;
        }

        // -------------------------------------------------------------------------
        // Matching
        // -------------------------------------------------------------------------

        /**
         * Checks whether the bytes at the current position match value.
         */
        public boolean matchesAscii(String value, int ooffset, int len) {
            if (len > remaining()) {
                return false;
            }

            if (this.current == null) {
                return false;
            }

            Block block = this.current;
            int blockOffset = this.currentOffset;
            int valueOffset = ooffset;
            int remaining = len;

            while (block != null && remaining > 0) {
                final MemorySegment segment = block.payload();
                final int available = written(block) - blockOffset;
                final int count = Math.min(available, remaining);

                for (int i = 0; i < count; i++) {
                    if (segment.get(ValueLayout.JAVA_BYTE, blockOffset + i) != value.charAt(valueOffset + i)) {
                        return false;
                    }
                }

                remaining -= count;
                valueOffset += count;
                blockOffset += count;

                if (remaining > 0) {
                    block = block.next();
                    blockOffset = 0;
                }
            }

            return true;
        }

        public boolean matchesAscii(String value) {
            return matchesAscii(value, 0, value.length());
        }

        /**
         * TODO after benchmarking, try 8-byte SWAR scanning?
         * Checks if the next characters match the expected string, ignoring the ASCII case.
         */
        public boolean matchesAsciiIgnoreCase(String expected, int ooffset, int len) {
            if (remaining() < len) {
                return false;
            }

            for (int i = 0; i < len; i++) {
                byte actual = getAt(this.position + i);
                char expectedChar = expected.charAt(i + ooffset);
                if (actual >= 'A' && actual <= 'Z') {
                    actual |= 0x20;
                }
                if (expectedChar >= 'A' && expectedChar <= 'Z') {
                    expectedChar |= 0x20;
                }
                if (actual != expectedChar) {
                    return false;
                }
            }

            return true;
        }

        public boolean matchesAsciiIgnoreCase(String expected) {
            final int length = expected.length();
            return matchesAsciiIgnoreCase(expected, 0, length);
        }

        /**
         * Consumes one byte if it matches.
         */
        public boolean consume(byte expected) {
            if (!hasRemaining()) {
                return false;
            }
            if (peek() != expected) {
                return false;
            }
            advance(1);
            return true;
        }

        /**
         * Consumes two bytes sequentially based on the wire's endianness.
         */
        public boolean consume(byte first, byte second) {
            if (remaining() < 2) {
                return false;
            }
            if (!peek(first, second)) {
                return false;
            }
            advance(2);
            return true;
        }

        /**
         * Consumes two bytes if they match.
         *
         * @param expected 2 byte native-endian numeric value
         */
        public boolean consume(short expected) {
            if (remaining() < 2) {
                return false;
            }
            if (peekShort() != expected) {
                return false;
            }
            advance(2);
            return true;
        }

        /**
         * Consumes an ASCII string if it matches.
         */
        public boolean consumeAscii(String value) {
            if (!matchesAscii(value)) {
                return false;
            }
            advance(value.length());
            return true;
        }

        // -------------------------------------------------------------------------
        // Search
        // -------------------------------------------------------------------------

        /**
         * Finds value starting from the current cursor position.
         * Does not modify the cursor.
         *
         * @return cursor relative position (length)
         */
        public int indexOf(byte value) {
            Block block = this.current;
            int blockOffset = this.currentOffset;
            int relativePos = 0;

            while (block != null) {
                final MemorySegment segment = block.payload();
                final int end = written(block);
                for (int i = blockOffset; i < end; i++) {
                    if (segment.get(ValueLayout.JAVA_BYTE, i) == value) {
                        return relativePos + (i - blockOffset);
                    }
                }
                relativePos += end - blockOffset;
                block = block.next();
                blockOffset = 0;
            }

            return -1;
        }

        /**
         * Finds a two-byte value starting from the current cursor position.
         * Handles block boundaries.
         * Checks two bytes on the wire sequentially. Therefore, it depends on the wire's endianness.
         *
         * @return cursor relative position (length)
         */
        public int indexOf(byte first, byte second) {
            Block block = this.current;
            int blockOffset = this.currentOffset;
            long relativePos = 0;

            while (block != null) {
                final MemorySegment segment = block.payload();
                final int end = written(block);
                // Fast path: both bytes reside in this block.
                for (int i = blockOffset; i < end - 1; i++) {
                    if (segment.get(ValueLayout.JAVA_BYTE, i) == first &&
                        segment.get(ValueLayout.JAVA_BYTE, i + 1) == second) {
                        return (int) (relativePos + (i - blockOffset));
                    }
                }
                // Slow path: first byte is the last byte of this block.
                if (blockOffset < end &&
                    segment.get(ValueLayout.JAVA_BYTE, end - 1) == first) {
                    final Block next = block.next();
                    if (next != null) {
                        final MemorySegment nextSegment = next.payload();
                        if (written(next) > 0 &&
                            nextSegment.get(ValueLayout.JAVA_BYTE, 0) == second) {
                            return (int) (relativePos + (end - blockOffset - 1));
                        }
                    }
                }
                relativePos += end - blockOffset;
                block = block.next();
                blockOffset = 0;
            }

            return -1;
        }

        /**
         * Finds value starting at an absolute offset.
         * Does not modify the cursor.
         *
         * @return offset relative position (length)
         */
        public int indexOf(byte value, int offset) {
            if (offset < 0) {
                throw new IllegalArgumentException("offset < 0");
            }
            if (offset >= limit()) {
                return -1;
            }
            final int oldPosition = this.position;
            locate(offset);
            final int result = indexOf(value);
            locate(oldPosition);

            return result;
        }

        // -------------------------------------------------------------------------
        // Relative string/bytes
        // -------------------------------------------------------------------------

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

        /**
         * Reads an ASCII string from the current position and advances.
         */
        public String getAscii(int length) {
            final String value = getAscii(this.position, length);
            advance(length);
            return value;
        }

        /**
         * Reads a UTF-8 string from the current position and advances.
         */
        public String getUtf8(int length) {
            final String value = getUtf8(this.position, length);
            advance(length);
            return value;
        }

        // -------------------------------------------------------------------------
        // Copy / zero-copy
        // -------------------------------------------------------------------------

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
                final MemorySegment segment = block.payload();
                final int available = written(block) - blockOffset;
                final int count = Math.min(available, remaining);

                MemorySegment.copy(
                    segment,
                    blockOffset,
                    MemorySegment.ofArray(destination),
                    destinationOffset + dest,
                    count
                );

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
         * Zero-copy only when the range resides entirely inside one block.
         */
        public MemorySegment segment(int offset, int length) {
            if (length < 0 || offset < 0 || (long) offset + length > limit()) {
                throw new IndexOutOfBoundsException();
            }
            locate(offset);
            if (this.current == null) {
                return length == 0 ? MemorySegment.NULL : null;
            }

            final MemorySegment segment = this.current.payload();
            if (this.currentOffset + length > written(this.current)) {
                return null;
            }

            return segment.asSlice(this.currentOffset, length);
        }

        // -------------------------------------------------------------------------
        // Internal cursor mechanics
        // -------------------------------------------------------------------------

        private void ensureAvailable() {
            if (this.current == null) {
                throw new IndexOutOfBoundsException("No remaining bytes");
            }
            if (this.currentOffset < written(this.current)) {
                return;
            }

            this.current = this.current.next();
            this.currentOffset = 0;
            if (this.current == null) {
                throw new IndexOutOfBoundsException("No remaining bytes");
            }
        }

        private short peekShort() {
            final MemorySegment segment = this.current.payload();

            if (this.currentOffset + 2 <= written(this.current)) {
                return segment.get(ValueLayout.JAVA_SHORT_UNALIGNED, this.currentOffset);
            }

            final int high = getAt(this.position) & 0xff;
            final int low = getAt(this.position + 1) & 0xff;

            return (short) ((high << 8) | low);
        }

        private boolean peek(byte first, byte second) {
            final Block block = this.current;
            final int offset = this.currentOffset;
            if (block == null) return false;

            final MemorySegment segment = block.payload();
            final int end = written(block);
            // Same block
            if (offset + 1 < end) {
                return segment.get(ValueLayout.JAVA_BYTE, offset) == first
                    && segment.get(ValueLayout.JAVA_BYTE, offset + 1) == second;
            }
            // Cross-block
            if (offset == end - 1) {
                final Block next = block.next();
                if (next == null || written(next) == 0) {
                    return false;
                }
                return segment.get(ValueLayout.JAVA_BYTE, offset) == first
                    && next.payload().get(ValueLayout.JAVA_BYTE, 0) == second;
            }
            return false;
        }

        public byte getAt(int position) {
            Block block = this.head;
            long logical = 0;

            while (block != null) {
                final MemorySegment segment = block.payload();
                final long size = written(block);
                if (position < logical + size) {
                    return segment.get(ValueLayout.JAVA_BYTE, position - logical);
                }
                logical += size;
                block = block.next();
            }

            throw new IndexOutOfBoundsException("Position outside of buffer: " + position);
        }

        private void advance(int count) {
            int remaining = count;

            while (remaining > 0) {
                ensureAvailable();
                final int available = written(this.current) - this.currentOffset;
                final int step = Math.min(available, remaining);

                this.currentOffset += step;
                this.position += step;
                remaining -= step;

                if (this.currentOffset == written(this.current)) {
                    this.current = this.current.next();
                    this.currentOffset = 0;
                }
            }
        }

        private void locate(int target) {
            if (target < 0 || target > limit()) {
                throw new IndexOutOfBoundsException("Position outside of buffer: " + target);
            }
            if (target == this.position) {
                return;
            }

            Block block = this.head;
            long logical = 0;
            while (block != null) {
                final long size = block.payload().byteSize();
                if (target < logical + size) {
                    this.current = block;
                    this.currentOffset = (int) (target - logical);
                    this.position = target;
                    return;
                }
                logical += size;
                block = block.next();
            }
            // target == limit()
            this.current = null;
            this.currentOffset = 0;
            this.position = target;
        }
    }

}
