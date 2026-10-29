package io.javafast.transport

import io.javafast.test.TransportUtils
import spock.lang.Specification

import java.nio.ByteBuffer
import java.nio.ByteOrder

class TransportBufferReaderSpec extends Specification implements TransportUtils {

    def 'consume byte should advance position only when the byte matches'() {
        given:
        def buffer = TransportBuffer.create(64, 1)
        def writer = buffer.writer()
        writer.writeBytes('ab'.bytes)
        def reader = writer.reader()

        expect:
        reader.consume((byte) 'a')
        reader.position() == 1
        !reader.consume((byte) 'c')
        reader.position() == 1
        reader.consume((byte) 'b')
        reader.position() == 2
        !reader.consume((byte) 'd')
        reader.position() == 2

        cleanup:
        writer.release()
        buffer.close()
    }

    def 'consume two bytes should advance position only when both bytes match'() {
        given:
        def buffer = TransportBuffer.create(64, 1)
        def writer = buffer.writer()
        writer.writeBytes('abcd'.bytes)
        def reader = writer.reader()

        expect:
        !reader.consume((byte) 'a', (byte) 'c')
        reader.position() == 0
        reader.consume((byte) 'a', (byte) 'b')
        reader.position() == 2
        !reader.consume((byte) 'c', (byte) 'e')
        reader.position() == 2
        reader.consume((byte) 'c', (byte) 'd')
        reader.position() == 4
        !reader.consume((byte) 'e', (byte) 'f')
        reader.position() == 4

        cleanup:
        writer.release()
        buffer.close()
    }

    def 'consume short should use native byte order and preserve position when it does not match'() {
        given:
        def expected = (short) 0x1234
        def bytes = ByteBuffer.allocate(2)
                .order(ByteOrder.nativeOrder())
                .putShort(expected)
                .array()
        def buffer = TransportBuffer.create(64, 1)
        def writer = buffer.writer()
        writer.writeBytes(bytes)
        def reader = writer.reader()

        expect:
        !reader.consume((short) 0x5678)
        reader.position() == 0
        reader.consume(expected)
        reader.position() == 2
        !reader.consume(expected)
        reader.position() == 2

        cleanup:
        writer.release()
        buffer.close()
    }

    def 'rewind should move position backwards across block boundaries'() {
        given:
        def buffer = TransportBuffer.create(64, 2) // payload size is 64 - 3*4 = 52
        def writer = buffer.writer()
        writer.writeBytes(('a' * 52 + 'bcdef').bytes)
        def reader = writer.reader()

        when:
        reader.skip(54) // at 'd'
        reader.rewind(3) // at 'a'

        then:
        reader.position() == 51
        reader.get() == (byte) 'a'
        reader.position() == 52
        reader.get() == (byte) 'b'
        reader.position() == 53

        cleanup:
        writer.release()
        buffer.close()
    }

    def 'rewind should reject negative length and rewinding past the beginning'() {
        given:
        def buffer = TransportBuffer.create(64, 1)
        def writer = buffer.writer()
        writer.writeBytes('abc'.bytes)
        def reader = writer.reader()

        when:
        reader.rewind(-1)

        then:
        thrown(IllegalArgumentException)
        reader.position() == 0

        when:
        reader.rewind(1)

        then:
        thrown(IndexOutOfBoundsException)
        reader.position() == 0

        cleanup:
        writer.release()
        buffer.close()
    }

    def 'unconsume should move position back when the previous byte matches'() {
        given:
        def buffer = TransportBuffer.create(64, 2)
        def writer = buffer.writer()
        writer.writeBytes(('x' * 64 + 'ab').bytes)
        def reader = writer.reader()

        when:
        reader.skip(65)

        then:
        reader.unconsume((byte) 'a')
        reader.position() == 64
        reader.get() == (byte) 'a'
        reader.position() == 65
        !reader.unconsume((byte) 'x')
        reader.position() == 65

        cleanup:
        writer.release()
        buffer.close()
    }

    def 'unconsume should fail at the beginning or when the previous byte does not match'() {
        given:
        def buffer = TransportBuffer.create(64, 1)
        def writer = buffer.writer()
        writer.writeBytes('ab'.bytes)
        def reader = writer.reader()

        expect:
        !reader.unconsume((byte) 'a')
        reader.position() == 0

        when:
        reader.skip(2)

        then:
        !reader.unconsume((byte) 'a')
        reader.position() == 2
        reader.unconsume((byte) 'b')
        reader.position() == 1

        cleanup:
        writer.release()
        buffer.close()
    }

    def 'reader should throw exception at write limit'() {
        given:
        def buffer = TransportBuffer.create(64, 2)
        def writer = buffer.writer()
        writer.read('c'.repeat(52)) // limit = 52 = byteSize
        def reader = writer.reader()
        assert reader.position() == 0

        when: 'set position at limit is allowed'
        reader.position(52)
        then: 'reader advances into the next block'
        noExceptionThrown()
        reader.position() == 52
        (reader as TransportBuffer.ReaderImpl).current == buffer.blocks[1]
        (reader as TransportBuffer.ReaderImpl).currentOffset == 0

        when: 'get after limit is not allowed'
        reader.get()
        then:
        thrown(IndexOutOfBoundsException)

        when: 'set position after limit is not allowed'
        reader.position(53)

        then:
        thrown(IndexOutOfBoundsException)
    }

    def 'indexOf should find character at boundary positions'() {
        given:
        def buffer = TransportBuffer.create(64, 1)
        def writer = buffer.writer()
        writer.read('abcde')
        def reader = writer.reader()

        when:
        def result = reader.indexOf((byte) target)

        then:
        result == expectedIndex

        cleanup:
        writer.release()
        buffer.close()

        where:
        target      | expectedIndex
        'a' as char | 0
        'e' as char | 4
    }

    def 'indexOf should return -1 when character is not present'() {
        given:
        def buffer = TransportBuffer.create(64, 2)
        def writer = buffer.writer()
        writer.read('a'.repeat(112))
        def reader = writer.reader()

        expect:
        reader.indexOf((byte) ('z' as char)) == -1

        cleanup:
        writer.release()
        buffer.close()
    }

    def 'indexOf should find character across block boundary'() {
        given:
        def buffer = TransportBuffer.create(64, 2)
        def writer = buffer.writer()
        writer.read('ABCDEFGH12345678')
        def reader = writer.reader()

        expect:
        reader.indexOf((byte) ('1' as char)) == 8

        cleanup:
        writer.release()
        buffer.close()
    }

}
