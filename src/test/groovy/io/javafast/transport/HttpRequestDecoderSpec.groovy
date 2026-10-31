package io.javafast.transport

import io.javafast.http.HttpMethod
import io.javafast.http.HttpRequestException
import io.javafast.test.TestUtils
import spock.lang.Specification

class HttpRequestDecoderSpec extends Specification implements TestUtils {

    def "should decode method target and headers from http1.1 wire format"() {
        given:
        def buffer = TransportBuffer.create(256, 2)
        def decoder = new HttpRequestDecoder()
        def writer = buffer.writer()
        def reader = writer.reader()
        writer.writeUtf8('POST /users/42?active=true HTTP/1.1\r\n' +
          'Host:localhost\r\n' +
          'Content-length:0\r\n' +
          'X-test:abc\r\n' +
          '\r\n'
        )

        when:
        decoder.parse(reader)

        then:
        decoder.method == HttpMethod.POST
        decoder.host.toString() == 'localhost'
        decoder.target.toString() == '/users/42?active=true'
        decoder.contentLength == 0
        !decoder.chunked
        decoder.getHeaderName(0) == 'Host'
        decoder.getHeaderName(1) == 'Content-length'
        decoder.getHeaderName(2) == 'X-test'
        decoder.getHeader(0) == 'localhost'
        decoder.getHeader(1) == '0'
        decoder.getHeader(2) == 'abc'
        decoder.getBodyAsString() == ''

        cleanup:
        writer.release()
        buffer.close()
    }

    def "should decode body"() {
        given:
        def buffer = TransportBuffer.create(256, 2)
        def decoder = new HttpRequestDecoder()
        def writer = buffer.writer()
        def reader = writer.reader()
        writer.writeUtf8('POST /users/42?active=true HTTP/1.1\r\n' +
          'Host:localhost\r\n' +
          'Content-length:128\r\n' +
          '\r\n' +
          'b' + 'o'.repeat(127)
        )

        when:
        decoder.parse(reader)

        then:
        decoder.method == HttpMethod.POST
        decoder.target.toString() == '/users/42?active=true'
        decoder.contentLength == 128
        !decoder.chunked
        decoder.getBodyAsString() == 'b' + 'o'.repeat(127)

        cleanup:
        writer.release()
        buffer.close()
    }

    def "should detect chunked transfer encoding"() {
        given:
        def buffer = TransportBuffer.create(256, 2)
        def decoder = new HttpRequestDecoder()

        when: 'supported multiple codes'
        def writer = buffer.writer()
        def reader = writer.reader()
        writer.writeUtf8('GET /stream HTTP/1.1\r\n' +
          'Host:localhost\r\n' +
          'Transfer-Encoding:gzip, chunked\r\n' +
          '\r\n'
        )

        and:
        decoder.parse(reader)

        then:
        decoder.method == HttpMethod.GET
        decoder.target.toString() == '/stream'
        decoder.chunked

        when: 'supported single code'
        writer.release()
        writer = buffer.writer()
        reader = writer.reader()
        writer.writeUtf8('GET /stream HTTP/1.1\r\n' +
          'Host:localhost\r\n' +
          'Transfer-Encoding:chunked\r\n' +
          '\r\n'
        )

        and:
        decoder.parse(reader)

        then:
        decoder.method == HttpMethod.GET
        decoder.target.toString() == '/stream'
        decoder.chunked

        when: 'supported with OWS'
        writer.release()
        writer = buffer.writer()
        reader = writer.reader()
        writer.writeUtf8('GET /stream HTTP/1.1\r\n' +
          'Host:localhost\r\n' +
          'Transfer-Encoding: chunked    \r\n' +
          '\r\n'
        )

        and:
        decoder.parse(reader)

        then:
        decoder.method == HttpMethod.GET
        decoder.target.toString() == '/stream'
        decoder.chunked

        when: 'unsupported transfer encoding'
        writer.release()
        writer = buffer.writer()
        reader = writer.reader()
        writer.writeUtf8('GET /stream HTTP/1.1\r\n' +
          'Host:localhost\r\n' +
          'Transfer-Encoding:chunked, gzip\r\n' + // not valid in RFC 9112
          '\r\n'
        )

        and:
        decoder.parse(reader)

        then:
        thrown(HttpRequestException)

        when: 'unsupported transfer encoding'
        writer.release()
        writer = buffer.writer()
        reader = writer.reader()
        writer.writeUtf8('GET /stream HTTP/1.1\r\n' +
          'Host:localhost\r\n' +
          'Transfer-Encoding:gzip\r\n' + // not valid in RFC 9112
          '\r\n'
        )

        and:
        decoder.parse(reader)

        then:
        thrown(HttpRequestException)

        cleanup:
        writer.release()
        buffer.close()
    }

    def "should decode headers with empty spaces"() {
        given:
        def buffer = TransportBuffer.create(256, 2)
        def decoder = new HttpRequestDecoder()

        def writer = buffer.writer()
        def reader = writer.reader()
        writer.writeUtf8('POST /users/42?active=true HTTP/1.1\r\n' +
          'Host: example.com  \r\n' +
          'Content-Length: 0  \r\n' +
          'X-Test:  abc efg   \r\n' +
          '\r\n'
        )

        when:
        decoder.parse(reader)

        then:
        decoder.method == HttpMethod.POST
        decoder.target.toString() == '/users/42?active=true'
        decoder.contentLength == 0
        !decoder.chunked
        decoder.getHeaderName(0) == 'Host'
        decoder.getHeaderName(1) == 'Content-Length'
        decoder.getHeaderName(2) == 'X-Test'
        decoder.getHeader(0) == 'example.com'
        decoder.getHeader(1) == '0'
        decoder.getHeader(2) == 'abc efg'
        decoder.getBodyAsString() == ''

        cleanup:
        writer.release()
        buffer.close()
    }

    def "should throw malformed when invalid or missing host header"() {
        given:
        def buffer = TransportBuffer.create(256, 2)
        def decoder = new HttpRequestDecoder()

        def writer = buffer.writer()
        def reader = writer.reader()
        writer.writeUtf8('GET /users HTTP/1.1\r\n' +
          'Content-Length:0\r\n' +
          '\r\n'
        )

        when: 'host header is missing'
        decoder.parse(reader)

        then:
        def e = thrown(HttpRequestException)
        e == HttpRequestException.MALFORMED

        when: 'host header is invalid'
        writer.release()
        writer = buffer.writer()
        reader = writer.reader()
        writer.writeUtf8('GET /users HTTP/1.1\r\n' +
          'Host:invalid@example.com\r\n' +
          '\r\n'
        )
        decoder.parse(reader)

        then:
        e = thrown(HttpRequestException)
        e == HttpRequestException.MALFORMED

        when: 'host header ipv6 is invalid'
        writer.release()
        writer = buffer.writer()
        reader = writer.reader()
        writer.writeUtf8('GET /users HTTP/1.1\r\n' +
          'Host:[invalid]\r\n' +
          '\r\n'
        )
        decoder.parse(reader)

        then:
        e = thrown(HttpRequestException)
        e == HttpRequestException.MALFORMED

        cleanup:
        writer.release()
        buffer.close()
    }

    def "should throw malformed when header is not valid according to RFC 9110"() {
        given:
        def buffer = TransportBuffer.create(128, 1)
        def decoder = new HttpRequestDecoder()
        def ex = exchange('GET', '/users')
        ex.requestHeaders.clear()
        ex.requestHeaders.add('Host', 'localhost')
        ex.requestHeaders.add(':X X@X', 'not valid')

        def writer = buffer.writer()
        def reader = writer.reader()
        writer.writeUtf8('GET /users HTTP/1.1\r\n' +
          'Host: localhost\r\n' +
          ':X X@X:not valid\r\n' +
          '\r\n'
        )

        when: 'header name has spaces'
        decoder.parse(reader)

        then:
        def e = thrown(HttpRequestException)
        e == HttpRequestException.MALFORMED

        when: 'header name tchars are valid'
        writer.release()
        writer = buffer.writer()
        reader = writer.reader()
        writer.writeUtf8('GET /users HTTP/1.1\r\n' +
          'Host: localhost\r\n' +
          '!X#X\$X%X&X\'X*X+X-X.X^X_X`X|X~:valid\r\n' +
          '\r\n'
        )

        and:
        decoder.parse(reader)

        then:
        noExceptionThrown()

        cleanup:
        writer.release()
        buffer.close()
    }
}
