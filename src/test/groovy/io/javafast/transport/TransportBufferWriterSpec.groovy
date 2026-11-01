package io.javafast.transport

import io.javafast.test.TransportUtils
import spock.lang.Specification

import java.nio.BufferOverflowException
import java.nio.charset.StandardCharsets

class TransportBufferWriterSpec extends Specification implements TransportUtils {

    private static final int BLOCK_SIZE = 64
    private static final int PAYLOAD_SIZE = BLOCK_SIZE - 12

    private TransportBuffer buffer

    def setup() {
        buffer = TransportBuffer.create(BLOCK_SIZE, 4)
    }

    def cleanup() {
        buffer.close()
    }

    private void useBlocks(int count) {
        buffer.close()
        buffer = TransportBuffer.create(BLOCK_SIZE, count)
    }

    def "writeByte writes a single byte"() {
        given:
        def writer = buffer.writer()

        when:
        writer.writeByte((byte) 0x41)
        writer.writeByte((byte) 0x00)
        writer.writeByte((byte) 0xFF)

        then:
        writer.totalWritten() == 3
        readAll(writer) == ([0x41, 0x00, 0xFF] as byte[])
    }

    def "writeBytes writes the requested array range into a single block"() {
        given:
        def writer = buffer.writer()
        byte[] source = [10, 20, 30, 40, 50, 60] as byte[]

        when:
        writer.writeBytes(source, 2, 3)

        then:
        writer.totalWritten() == 3
        readAll(writer) == ([30, 40, 50] as byte[])
    }

    def "writeBytes with zero length writes nothing"() {
        given:
        def writer = buffer.writer()
        byte[] source = [10, 20, 30] as byte[]

        when:
        writer.writeBytes(source, 1, 0)

        then:
        writer.totalWritten() == 0
        readAll(writer).length == 0
    }

    def "writeBytes spans multiple blocks without losing or reordering bytes"() {
        given:
        def writer = buffer.writer()
        byte[] source = new byte[PAYLOAD_SIZE * 2 + 13]
        new Random(42).nextBytes(source)

        when:
        writer.writeBytes(source, 0, source.length)

        then:
        writer.totalWritten() == source.length
        readAll(writer) == source
    }

    def "writeByte spans multiple blocks"() {
        given:
        def writer = buffer.writer()
        byte[] expected = new byte[PAYLOAD_SIZE + 7]

        when:
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i * 3)
            writer.writeByte(expected[i])
        }

        then:
        writer.totalWritten() == expected.length
        readAll(writer) == expected
    }

    def "writeUtf8 encodes Unicode without treating chars as bytes"() {
        given:
        def writer = buffer.writer()
        def value = "A héllo € 你好 😀"

        when:
        writer.writeUtf8(value)
        then:
        writer.totalWritten() == value.getBytes("UTF-8").length
        readAll(writer) == value.getBytes("UTF-8")

        when:
        useBlocks(1)
        writer = buffer.writer()
        value = "\u0000\u007F\u0080\u0800"
        writer.writeUtf8(value)
        then:
        writer.totalWritten() == value.getBytes("UTF-8").length
        writer.reader().getUtf8(0, writer.totalWritten()) == value
    }

    def "writeUtf8 handles UTF-16 ranges and block boundaries"() {
        given:
        useBlocks(2)
        def writer = buffer.writer()
        def value = ("a" * (PAYLOAD_SIZE - 1)) + "€"

        when:
        writer.writeUtf8(value, 0, value.length())

        then:
        writer.totalWritten() == value.getBytes("UTF-8").length
        readAll(writer) == value.getBytes("UTF-8")
    }

    def "writeUtf8 replaces an unpaired surrogate like the standard UTF-8 encoder"() {
        given:
        def writer = buffer.writer()
        def value = "a\uD800b\uDC00"

        when:
        writer.writeUtf8(value)

        then:
        writer.totalWritten() == 4
        readAll(writer) == value.getBytes("UTF-8")
        writer.reader().getUtf8(0, 4) == "a?b?"
    }

    def "writeUtf8 should handle invalid ranges"() {
        given:
        def writer = buffer.writer()

        when: 'empty string range'
        writer.writeUtf8("Hello", 5, 0)
        then: 'no bytes written'
        writer.totalWritten() == 0

        when: 'out of index'
        writer.writeUtf8("Hello", 6, 0)
        then: 'throws exception'
        thrown(IndexOutOfBoundsException)
    }

    def "utf8length calculates byte count correctly"() {
        expect:
        TransportBuffer.Writer.utf8Length(text) == text.getBytes("UTF-8").length
        where:
        text << [
          "A héllo € 你好 😀",
          "a\uD800b\uDC00",
          "\u0000\u007F\u0080\u0800"
        ]
    }

    def "writeBytes and writeByte can be mixed across a block boundary"() {
        given:
        def writer = buffer.writer()
        byte[] prefix = new byte[PAYLOAD_SIZE - 2]
        byte[] suffix = [101, 102, 103, 104, 105] as byte[]
        Arrays.fill(prefix, (byte) 7)

        when:
        writer.writeBytes(prefix, 0, prefix.length)
        writer.writeByte((byte) 8)
        writer.writeByte((byte) 9)
        writer.writeBytes(suffix, 0, suffix.length)

        then:
        byte[] expected = (prefix.toList() + [8, 9] + suffix.toList()).collect { it as byte }.toArray(new byte[0])
        writer.totalWritten() == expected.length
        readAll(writer) == expected
    }

    def "writeByte throws when the buffer has no free block"() {
        given:
        useBlocks(1)
        def writer = buffer.writer()

        when:
        PAYLOAD_SIZE.times {
            writer.writeByte((byte) 1)
        }
        writer.writeByte((byte) 2)

        then:
        thrown(BufferOverflowException)
        writer.totalWritten() == PAYLOAD_SIZE
    }

    def "writeBytes throws when the data exceeds total buffer capacity"() {
        given:
        useBlocks(2)
        def writer = buffer.writer()
        byte[] source = new byte[PAYLOAD_SIZE * 2 + 1]
        new Random(42).nextBytes(source)

        when:
        writer.writeBytes(source, 0, source.length)

        then:
        thrown(BufferOverflowException)

        and:
        writer.totalWritten() == PAYLOAD_SIZE * 2
        readAll(writer) == Arrays.copyOf(source, PAYLOAD_SIZE * 2)
    }

    def "writeBytes rejects an invalid array range"() {
        given:
        def writer = buffer.writer()
        byte[] source = [1, 2, 3] as byte[]

        when:
        writer.writeBytes(source, 2, 2)

        then:
        thrown(IndexOutOfBoundsException)

        and:
        writer.totalWritten() == 0
    }

    def 'should fork the request when its boundary is in the middle of a block'() {
        given:
        def buffer = TransportBuffer.create(BLOCK_SIZE, 4)
        def writer = buffer.writer()
        writer.writeAscii('first-requestsecond-request')

        when:
        def fork = writer.fork('first-request'.size())

        then:
        readAll(fork) == 'second-request'.getBytes(StandardCharsets.US_ASCII)

        cleanup:
        buffer.close()
    }

    def 'should fork the request when its boundary is exactly at the end of a block'() {
        given:
        def buffer = TransportBuffer.create(BLOCK_SIZE, 4)
        def writer = buffer.writer()
        def first = 'a' * PAYLOAD_SIZE
        writer.writeAscii(first + 'second-request')

        when:
        def fork = writer.fork(first.size())

        then:
        readAll(fork) == 'second-request'.getBytes(StandardCharsets.US_ASCII)

        cleanup:
        buffer.close()
    }

    def 'should preserve all bytes when the source spans multiple blocks'() {
        given:
        def buffer = TransportBuffer.create(BLOCK_SIZE, 64)
        def writer = buffer.writer()
        def source = ('source-' * (PAYLOAD_SIZE + 3)).getBytes(StandardCharsets.US_ASCII)

        when:
        writer.writeBytes(source)

        then:
        readAll(writer) == source

        cleanup:
        buffer.close()
    }

    def 'should preserve all bytes when the fork spans multiple blocks'() {
        given:
        def buffer = TransportBuffer.create(BLOCK_SIZE, 32)
        def writer = buffer.writer()
        def prefix = 'prefix'
        def request = ('forked-request-' * (PAYLOAD_SIZE + 3)).getBytes(StandardCharsets.US_ASCII)
        writer.writeAscii(prefix)
        writer.writeBytes(request)

        when:
        def fork = writer.fork(prefix.size())

        then:
        readAll(fork) == request

        cleanup:
        buffer.close()
    }

    def 'should not change the original reader position when forking'() {
        given:
        def buffer = TransportBuffer.create(BLOCK_SIZE, 32)
        def writer = buffer.writer()
        writer.writeAscii('prefix-and-request')
        def reader = writer.reader()
        reader.position(3)

        when:
        def fork = writer.fork('prefix-'.size())

        then:
        reader.position() == 3
        readAll(fork) == 'and-request'.getBytes(StandardCharsets.US_ASCII)

        cleanup:
        buffer.close()
    }

    def 'should keep fork bytes after releasing the original writer'() {
        given:
        def buffer = TransportBuffer.create(BLOCK_SIZE, 64)
        def writer = buffer.writer()
        def original = 'original-request-' * (PAYLOAD_SIZE + 2)
        writer.writeAscii(original)
        def expected = original.substring('original-'.size()).getBytes(StandardCharsets.US_ASCII)

        when:
        def fork = writer.fork('original-'.size())
        writer.release()

        then:
        readAll(fork) == expected

        cleanup:
        buffer.close()
    }


}
