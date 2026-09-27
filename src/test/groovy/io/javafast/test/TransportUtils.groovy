package io.javafast.test

import io.javafast.transport.TransportBuffer
import io.javafast.transport.TransportTestSource

import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer

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
            def readSize = Math.min(payload.capacity(), remaining)
            def chunk = new byte[readSize]
            payload.slice(0, readSize).get(chunk)
            System.arraycopy(chunk, 0, bytes, offset, readSize)
            offset += readSize
            current = buffer.getNext(current)
        }
        assert offset == expectedLength
        bytes
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
            this.chunkSizes = chunkSizes
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

}
