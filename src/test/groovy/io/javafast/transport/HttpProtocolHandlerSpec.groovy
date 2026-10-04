package io.javafast.transport

import io.javafast.test.TestUtils
import spock.lang.Specification

class HttpProtocolHandlerSpec extends Specification implements TestUtils {

    def "should mark payload complete for request without body"() {
        given:
        def http = encodedExchange('GET', '/status')
        def handler = new HttpProtocolHandler()

        when:
        handler.onBytes(http.reader, (int) http.reader.remaining())

        then:
        handler.payloadComplete()

        cleanup:
        http.block.release()
        http.buffer.close()
    }

    def "should mark payload complete for zero content length"() {
        given:
        def http = encodedExchange('POST', '/users', '', ['Content-Length': '0'])
        def handler = new HttpProtocolHandler()

        when:
        handler.onBytes(http.reader, (int) http.reader.remaining())

        then:
        handler.payloadComplete()

        cleanup:
        http.block.release()
        http.buffer.close()
    }

    def "should require all body bytes before marking payload complete"() {
        given:
        def http = encodedExchange('POST', '/users', 'hello', ['Content-Length': '5'])
        def handler = new HttpProtocolHandler()

        when: 'only part of the body arrives'
        handler.onBytes(http.reader, 2)

        then:
        !handler.payloadComplete()

        when: 'remaining body arrives'
        handler.onBytes(http.reader, 3)

        then:
        handler.payloadComplete()

        cleanup:
        http.block.release()
        http.buffer.close()
    }

    def "should parse content-length with case-insensitive name and leading whitespace"() {
        given:
        def http = encodedExchange('POST', '/users', 'hello', ['cOnTeNt-LeNgTh': '\t 5'])
        def handler = new HttpProtocolHandler()

        when:
        handler.onBytes(http.reader, (int) http.reader.remaining())

        then:
        handler.payloadComplete()

        cleanup:
        http.block.release()
        http.buffer.close()
    }

    def "should stop content-length parsing at first non-digit"() {
        given:
        def http = encodedExchange('POST', '/users', 'hello12345', ['Content-Length': '5abc'])
        def handler = new HttpProtocolHandler()

        when:
        handler.onBytes(http.reader, (int) http.reader.remaining())

        then:
        handler.payloadComplete()

        cleanup:
        http.block.release()
        http.buffer.close()
    }

    def "should reset internal state for next request"() {
        given:
        def first = encodedExchange('POST', '/users', 'hello', ['Content-Length': '5'])
        def second = encodedExchange('GET', '/health')
        def handler = new HttpProtocolHandler()

        when:
        handler.onBytes(first.reader, (int) first.reader.remaining())

        then:
        handler.payloadComplete()

        when:
        handler.reset()
        handler.onBytes(second.reader, (int) second.reader.remaining())

        then:
        handler.payloadComplete()

        cleanup:
        first.block.release()
        first.buffer.close()
        second.block.release()
        second.buffer.close()
    }

    private Map encodedExchange(String method, String uri, String body = null, Map<String, String> headers = [:]) {
        def buffer = TransportBuffer.create(256, 2)
        def encoder = new HttpTestRequestEncoder()
        def ex = exchange(method, uri, body)
        headers.each { name, value ->
            ex.requestHeaders.add(name, value)
        }

        def block = encoder.encode(ex, buffer)
        [buffer: buffer, block: block, reader: block.reader()]
    }
}
