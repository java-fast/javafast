package io.javafast.transport

import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

import java.lang.foreign.MemorySegment
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class TransferBufferSpec extends Specification {

    def 'should write and claim a single-block request'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payload = bytes(24)

        when:
        def written = buffer.write(sourceFromBytes(payload, [5, 7, 12]))
        def claim = buffer.claim()

        then:
        written
        claim != null
        readBytes(claim, payload.length) == payload
        claim.next() == null
        buffer.claim() == null

        when:
        buffer.release(claim)

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should chain multiple physical blocks for one logical request'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payload = bytes(140)

        when:
        def written = buffer.write(sourceFromBytes(payload, [17, 23, 31, 29, 40]))
        def claim = buffer.claim()
        def chain = chain(claim)

        then:
        written
        claim != null
        chain.size() >= 3
        chain*.toString().toSet().size() == chain.size()
        readBytes(claim, payload.length) == payload

        when:
        buffer.release(claim)

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should rollback unpublished chain when capacity is exhausted'() {
        given:
        def buffer = TransportBuffer.create(64, 2)
        def payload = bytes(buffer.capacity() + 1)

        when:
        def written = buffer.write(sourceFromBytes(payload, [59]))

        then:
        !written
        buffer.claim() == null
        buffer.freeBlockCount() == 2

        cleanup:
        buffer.close()
    }

    def 'should reject invalid source result and rollback acquired blocks'() {
        given:
        def buffer = TransportBuffer.create(64, 4)
        def invalidSource = new TransportSource() {
            @Override
            int read(MemorySegment destination) {
                return 0
            }
        }

        when:
        buffer.write(invalidSource)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains('Invalid TransportSource.read() result')
        buffer.claim() == null
        buffer.freeBlockCount() == 4

        cleanup:
        buffer.close()
    }

    def 'should allow only one consumer to claim after a single write'() {
        given:
        def buffer = TransportBuffer.create(64, 16)
        def payload = bytes(48)
        assert buffer.write(sourceFromBytes(payload, [9, 11, 13, 15]))

        def consumerCount = 24
        def ac = new AsyncConditions(consumerCount)
        def claims = new ConcurrentLinkedQueue<TransportBuffer.Block>()
        def pool = Executors.newFixedThreadPool(consumerCount)

        when:
        consumerCount.times {
            pool.submit {
                def claim = buffer.claim()
                if (claim != null) {
                    claims.add(claim)
                }
                ac.evaluate { assert true }
            }
        }
        ac.await()
        pool.close()

        then:
        claims.size() == 1
        readBytes(claims.peek(), payload.length) == payload
        buffer.claim() == null

        when:
        buffer.release(claims.peek())

        then:
        buffer.freeBlockCount() == 16

        cleanup:
        buffer.close()
    }

    private static List<TransportBuffer.Block> chain(TransportBuffer.Block head) {
        def chain = [] as List<TransportBuffer.Block>
        def current = head
        while (current != null) {
            chain.add(current)
            current = current.next()
        }
        chain
    }

    private static byte[] readBytes(TransportBuffer.Block head, int expectedLength) {
        def bytes = new byte[expectedLength]
        def offset = 0
        def current = head
        while (current != null && offset < expectedLength) {
            def remaining = expectedLength - offset
            def payload = current.payload()
            def readSize = Math.min((int) payload.byteSize(), remaining)
            def chunk = new byte[readSize]
            payload.asSlice(0, readSize).asByteBuffer().get(chunk)
            System.arraycopy(chunk, 0, bytes, offset, readSize)
            offset += readSize
            current = current.next()
        }
        assert offset == expectedLength
        bytes
    }

    private static TransportSource sourceFromBytes(byte[] data, List<Integer> chunkSizes = []) {
        new TransportSource() {
            private final MemorySegment source = MemorySegment.ofArray(data)
            private int offset = 0
            private int chunkIndex = 0

            @Override
            int read(MemorySegment destination) {
                if (offset >= data.length) {
                    return -1
                }
                int maxWritable = (int) destination.byteSize()
                int requested = chunkSizes.isEmpty() ? Integer.MAX_VALUE : chunkSizes[Math.min(chunkIndex, chunkSizes.size() - 1)]
                chunkIndex++
                int available = data.length - offset
                int toWrite = Math.min(Math.min(requested, maxWritable), available)
                destination.asSlice(0, toWrite).copyFrom(source.asSlice(offset, toWrite))
                offset += toWrite
                return toWrite
            }
        }
    }

    private static byte[] bytes(int length) {
        def data = new byte[length]
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i % 127)
        }
        data
    }
}
