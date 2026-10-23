package io.javafast.test

import io.javafast.transport.TransportBuffer
import io.javafast.transport.TransportTestSource

import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.charset.StandardCharsets

trait TransportUtils {

    List<TransportBuffer.Block> chain(TransportBuffer buffer, TransportBuffer.Block head) {
        def chain = [] as List<TransportBuffer.Block>
        def current = head
        while (current != null) {
            chain.add(current)
            current = getNext(buffer, current)
        }
        chain
    }

    byte[] readBytes(TransportBuffer buffer, TransportBuffer.Block head, int expectedLength) {
        def bytes = new byte[expectedLength]
        def offset = 0
        def current = head.index
        while (current != TransportBuffer.NEXT_NONE && offset < expectedLength) {
            def remaining = expectedLength - offset
            def payload = buffer.blocks[current].payload()
            def readSize = Math.min((int) payload.byteSize(), remaining)
            def chunk = new byte[readSize]
            payload.asByteBuffer().slice(0, readSize).get(chunk)
            System.arraycopy(chunk, 0, bytes, offset, readSize)
            offset += readSize
            current = buffer.getNext(current)
        }
        assert offset == expectedLength
        bytes
    }

    byte[] readAll(TransportBuffer.Writer writer) {
        def reader = writer.reader()
        reader.reset()
        byte[] result = new byte[reader.limit()]
        for (int i = 0; i < result.length; i++) {
            result[i] = reader.getAt(i)
        }
        return result
    }

    boolean hasNext(TransportBuffer buffer, TransportBuffer.Block block) {
        buffer.getNext(block.index) != TransportBuffer.NEXT_NONE
    }

    TransportBuffer.Block getNext(TransportBuffer buffer, TransportBuffer.Block block) {
        def next = buffer.getNext(block.index)
        if (next == TransportBuffer.NEXT_NONE) return null
        buffer.blocks[next]
    }

    static class TransportTestSourceImpl implements TransportTestSource {

        private final MemorySegment source
        private final List<Integer> chunkSizes

        private int offset = 0
        private int chunkIndex = 0

        TransportTestSourceImpl(byte[] data, List<Integer> chunkSizes) {
            this.chunkSizes = new ArrayList<>(chunkSizes) // copy protection since the array can be mutated
            source = MemorySegment.ofArray(data)
        }

        @Override
        int read(ByteBuffer destination) {
            if (offset >= source.byteSize()) {
                return -1
            }
            int maxWritable = destination.remaining()
            def minIndex = Math.min(chunkIndex, chunkSizes.size() - 1)
            int requested = chunkSizes.isEmpty() ? Integer.MAX_VALUE : chunkSizes[minIndex]
            int available = source.byteSize() as int - offset
            int toWrite = Math.min(Math.min(requested, maxWritable), available)
            if (requested > toWrite) { // handle partial chunk write
                chunkSizes[minIndex] = requested - toWrite
            } else {
                chunkIndex++
            }
            destination.put(this.source.asSlice(offset, toWrite).asByteBuffer())
            offset += toWrite
            return toWrite
        }
    }

    TransportTestSource transportSource(byte[] data, List<Integer> chunkSizes = []) {
        new TransportTestSourceImpl(data, chunkSizes)
    }

    byte[] bytes(int seed = 0, int length) {
        def data = new byte[length]
        for (int i = 0; i < length; i++) {
            data[i] = (byte) ((seed + i) % 127)
        }
        data
    }

    String requestKey(byte[] payload) {
        Base64.encoder.encodeToString(payload)
    }

    /**
     * Reads only one byte at a time from a given string
     */
    static final class SlowByteChannel implements ReadableByteChannel {

        private final ByteBuffer source;

        private SlowByteChannel(String value) {
            this.source = ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        int read(ByteBuffer dst) {
            if (!this.source.hasRemaining()) {
                return -1; // EOF
            }

            if (!dst.hasRemaining()) {
                return 0; // no space to write
            }

            final int toWrite = Math.min(1, this.source.remaining());
            final int oldLimit = this.source.limit();
            this.source.limit(this.source.position() + toWrite);
            dst.put(this.source);
            this.source.limit(oldLimit);

            return toWrite;
        }

        @Override
        boolean isOpen() {
            return true;
        }

        @Override
        void close() {
            // no-op
        }
    }

}
