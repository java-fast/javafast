package io.javafast.internal


import io.javafast.test.TransportUtils
import io.javafast.transport.TransportBuffer
import spock.lang.Specification

class TransportBufferOutputStreamSpec extends Specification implements TransportUtils {

    private static final int BLOCK_SIZE = 64
    private static final int PAYLOAD_SIZE = BLOCK_SIZE - 8

    private TransportBuffer buffer
    private TransportBufferOutputStream output

    def setup() {
        buffer = TransportBuffer.create(BLOCK_SIZE, 4)
        output = new TransportBufferOutputStream()
    }

    def cleanup() {
        buffer.close()
    }

    def "write(int) writes a single byte"() {
        given:
        def writer = buffer.writer()
        output.reset(writer)

        when:
        output.write(0x41)
        output.write(0x00)
        output.write(0xFF)

        then:
        writer.totalWritten() == 3
        readAll(writer) == ([0x41, 0x00, 0xFF] as byte[])
    }

    def "write(byte[]) writes the entire array"() {
        given:
        def writer = buffer.writer()
        output.reset(writer)
        byte[] source = [10, 20, 30, 40, 50] as byte[]

        when:
        output.write(source)

        then:
        writer.totalWritten() == source.length
        readAll(writer) == source
    }

    def "write(byte[], offset, length) writes only the requested range"() {
        given:
        def writer = buffer.writer()
        output.reset(writer)
        byte[] source = [10, 20, 30, 40, 50, 60] as byte[]

        when:
        output.write(source, 2, 3)

        then:
        writer.totalWritten() == 3
        readAll(writer) == ([30, 40, 50] as byte[])
    }

    def "write with zero length writes nothing"() {
        given:
        def writer = buffer.writer()
        output.reset(writer)
        byte[] source = [1, 2, 3] as byte[]

        when:
        output.write(source, 1, 0)

        then:
        writer.totalWritten() == 0
        readAll(writer).length == 0
    }

    def "write spans multiple blocks without losing or reordering bytes"() {
        given:
        def writer = buffer.writer()
        output.reset(writer)
        byte[] source = new byte[PAYLOAD_SIZE * 2 + 13]
        new Random(42).nextBytes(source)

        when:
        output.write(source)

        then:
        writer.totalWritten() == source.length
        readAll(writer) == source
    }

    def "single-byte and array writes can be mixed across a block boundary"() {
        given:
        def writer = buffer.writer()
        output.reset(writer)

        byte[] prefix = new byte[PAYLOAD_SIZE - 2]
        byte[] suffix = [101, 102, 103, 104, 105] as byte[]
        Arrays.fill(prefix, (byte) 7)

        byte[] expected = new byte[prefix.length + 2 + suffix.length]
        System.arraycopy(prefix, 0, expected, 0, prefix.length)
        expected[prefix.length] = 8
        expected[prefix.length + 1] = 9
        System.arraycopy(suffix, 0, expected, prefix.length + 2, suffix.length)

        when:
        output.write(prefix)
        output.write(8)
        output.write(9)
        output.write(suffix)

        then:
        writer.totalWritten() == expected.length
        readAll(writer) == expected
    }

    def "write(byte[], offset, length) rejects an invalid range"() {
        given:
        def writer = buffer.writer()
        output.reset(writer)
        byte[] source = [1, 2, 3] as byte[]

        when:
        output.write(source, 2, 2)

        then:
        thrown(IndexOutOfBoundsException)
        writer.totalWritten() == 0
    }

    def "flush does not alter the written payload"() {
        given:
        def writer = buffer.writer()
        output.reset(writer)
        byte[] source = [1, 2, 3, 4] as byte[]

        when:
        output.write(source)
        output.flush()

        then:
        writer.totalWritten() == source.length
        readAll(writer) == source
    }
}
