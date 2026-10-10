package io.javafast.transport

import io.javafast.test.TransportUtils
import spock.lang.Specification

import java.nio.BufferOverflowException
import java.nio.ByteBuffer

class TransportBufferSpec extends Specification implements TransportUtils {

    def 'should acquire and write a single-block request'() {
        given:
        def buffer = TransportBuffer.create(64, 1)
        def payload = bytes(24)
        def source = transportSource(payload, [5, 7, 12])

        when:
        def writer = buffer.writer()
        def written = writer.read(source)
        written += writer.read(source)
        written += writer.read(source)

        then:
        written == 24
        readBytes(buffer, writer.head, payload.length) == payload
        !hasNext(buffer, writer.head)
        buffer.acquire() == null // cannot acquire, no more blocks left

        when:
        writer.release()

        then:
        buffer.freeBlockCount() == 1

        cleanup:
        buffer.close()
    }

    def 'should write a single block full without a new acquire' () {
        given:
        def buffer = TransportBuffer.create(128, 1)
        def payload = bytes(120) // 120 max. writable space
        def source = transportSource(payload, [60, 60])

        when:
        def writer = buffer.writer()
        def written = writer.read(source)
        written += writer.read(source)

        then:
        written == 120
        readBytes(buffer, writer.head, payload.length) == payload
        !hasNext(buffer, writer.head)
        buffer.acquire() == null // cannot acquire, no more blocks left

        when:
        writer.release()

        then:
        buffer.freeBlockCount() == 1

        cleanup:
        buffer.close()
    }

    def 'should chain multiple physical blocks for one logical request'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payload = bytes(140)
        def source = transportSource(payload, [17, 23, 31, 29, 40])

        when:
        def writer = source.copyTo(buffer)
        def head = writer.head
        def chain = chain(buffer, head)

        then:
        head != null
        writer.totalWritten() == 140
        chain.size() >= 3
        chain*.toString().toSet().size() == chain.size()
        readBytes(buffer, head, payload.length) == payload

        when:
        head.release()

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should stop when capacity is exhausted'() {
        given:
        def buffer = TransportBuffer.create(64, 2)
        def payload = bytes(buffer.capacity() + 1)
        def source = transportSource(payload, [59])

        when:
        def writer = source.copyTo(buffer)

        then:
        writer.totalWritten() == 56 + 56
        buffer.freeBlockCount() == 0

        cleanup:
        buffer.close()
    }

    def 'should zero-byte read source result zero writes'() {
        given:
        def buffer = TransportBuffer.create(64, 4)
        def notReadySource = new TransportTestSource() {
            @Override
            int read(ByteBuffer destination) {
                return 0
            }
        }

        when:
        def writer = notReadySource.copyTo(buffer)

        then: 'nothing written and acquired blocks are released'
        writer.totalWritten() == 0

        cleanup:
        buffer.close()
    }

    def 'should throw exception if the buffer is already closed'() {
        given:
        def buffer = TransportBuffer.create(64, 16)
        def payload = bytes(48)
        def source = transportSource(payload, [9, 11, 13, 15])
        assert source.copyTo(buffer).totalWritten() == 48

        when: 'cannot claim from a closed buffer'
        buffer.close()
        buffer.acquire()

        then:
        thrown(IllegalStateException)
    }

    def 'should throw exception if the block size or block count is not valid'() {
        when: 'invalid block size'
        TransportBuffer.create(1, 1)
        then:
        thrown(IllegalArgumentException)

        when: 'invalid block count - too small'
        TransportBuffer.create(64, 0)
        then:
        thrown(IllegalArgumentException)

        when: 'invalid block count - negative'
        TransportBuffer.create(64, -1)
        then:
        thrown(IllegalArgumentException)

        when: 'invalid block count - not a power of two'
        TransportBuffer.create(64, 3)
        then:
        thrown(IllegalArgumentException)
    }

}
