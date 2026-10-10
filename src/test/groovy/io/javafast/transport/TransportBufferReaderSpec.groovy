package io.javafast.transport

import io.javafast.test.TransportUtils
import spock.lang.Specification

class TransportBufferReaderSpec extends Specification implements TransportUtils {

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
