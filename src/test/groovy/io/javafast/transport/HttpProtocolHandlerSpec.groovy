package io.javafast.transport

import io.javafast.test.TestUtils
import io.javafast.test.TransportUtils
import spock.lang.Specification

class HttpProtocolHandlerSpec extends Specification implements TestUtils, TransportUtils {

    def "should throw when request size exceeds MAX_REQUEST_SIZE"() {
        given:
        def http = encodedExchange('GET', '/status')
        def handler = new HttpProtocolHandler()

        when:
        handler.onBytes(http.reader, (10 * (1 << 20)) + 1)

        then:
        def ex = thrown(HttpRequestException)
        ex.is(HttpRequestException.REQUEST_TOO_LARGE)

        cleanup:
        http.block.release()
        http.buffer.close()
    }

    def "should throw when header size exceeds MAX_HEADER_SIZE"() {
        given:
        def largeHeaderValue = 'a' * (1 << 13)
        def http = encodedExchange('GET', '/status', null, ['X-Large': largeHeaderValue], 16 * 1024)
        def handler = new HttpProtocolHandler()

        when:
        handler.onBytes(http.reader, http.reader.remaining())

        then:
        def ex = thrown(HttpRequestException)
        ex.is(HttpRequestException.REQUEST_TOO_LARGE)

        cleanup:
        http.block.release()
        http.buffer.close()
    }

    def "should throw when body size exceeds MAX_BODY_SIZE"() {
        given:
        def http = encodedExchange('POST', '/users', null, ['Content-Length': "${(1 << 23) + 1}"])
        def handler = new HttpProtocolHandler()

        when:
        handler.onBytes(http.reader, (int) http.reader.remaining())

        then:
        def ex = thrown(HttpRequestException)
        ex.is(HttpRequestException.REQUEST_TOO_LARGE)

        cleanup:
        http.block.release()
        http.buffer.close()
    }

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
        handler.onBytes(http.reader, 21)
        then:
        !handler.payloadComplete()

        when: 'only part of the body arrives'
        handler.onBytes(http.reader, 10)
        then:
        !handler.payloadComplete()

        when: 'only part of the body arrives'
        handler.onBytes(http.reader, 7)
        then:
        !handler.payloadComplete()

        when: 'only part of the body arrives'
        handler.onBytes(http.reader, 9)
        then:
        !handler.payloadComplete()

        when: 'remaining body arrives'
        handler.onBytes(http.reader, 18)

        then:
        handler.payloadComplete()
        handler.requestLength == 65
        handler.contentLength == 5
        handler.bodyRemaining == 0

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

    def "should handle slow responses correctly"() {
        given:
        def buffer = TransportBuffer.create(128, 2)
        def writer = buffer.writer()
        def multiRequestPartial = 'POST /users/42?foo=bar HTTP/1.1\r\n' +
          'Host: example.com\r\n' +
          'Content-Type: application/json\r\n' +
          'Content-Length: 19\r\n' +
          'X-Foo: bar\r\n' +
          '\r\n' +
          '{"name":"JavaFast"}'
        def requestLength = multiRequestPartial.length()
        multiRequestPartial += 'GET /use' // second request is partial
        def channel = new SlowByteChannel(multiRequestPartial)
        def reader = writer.reader()
        def handler = new HttpProtocolHandler()
        println("reading ${multiRequestPartial.length()} bytes slowly...")
        int total = 0

        when:
        int read
        while ((read = writer.read(channel)) !=-1 && !handler.payloadComplete()) {
            handler.onBytes(reader, read)
            total += read
        }

        then:
        noExceptionThrown()
        total == requestLength
        handler.payloadComplete()
        handler.requestLength == total
        handler.contentLengthFound
        handler.contentLength == 19
        handler.bodyRemaining == 0
        handler.state == HttpProtocolHandler.State.COMPLETE

        cleanup:
        writer.release()
        buffer.close()
        println("successfully parsed ${total} bytes")
    }

    private Map encodedExchange(String method, String uri, String body = null, Map<String, String> headers = [:], int bufferSize = 256) {
        def buffer = TransportBuffer.create(bufferSize, 2)
        def encoder = new HttpTestRequestEncoder()
        def exchange = exchange(method, uri, body)
        headers.each { name, value ->
            exchange.requestHeaders.add(name, value)
        }

        def block = encoder.encode(exchange, buffer)
        [buffer: buffer, block: block, reader: block.reader()]
    }
}
