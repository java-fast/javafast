package io.javafast.transport

import io.javafast.http.HttpMethod
import io.javafast.test.TestUtils
import spock.lang.Specification

class HttpRequestDecoderSpec extends Specification implements TestUtils {

    def "should decode method target and headers from http1.1 wire format"() {
        given:
        def buffer = TransportBuffer.create(256, 2)
        def encoder = new HttpRequestEncoder()
        def decoder = new HttpRequestDecoder()
        def ex = exchange('POST', '/users/42?active=true')
        ex.requestHeaders.add('Content-Length', '0')
        ex.requestHeaders.add('X-Test', 'abc')

        def block = encoder.encode(ex, buffer)
        def reader = block.reader()

        when:
        decoder.parse(reader)

        then:
        decoder.method == HttpMethod.POST
        decoder.target == '/users/42?active=true'
        decoder.contentLength == 0
        !decoder.chunked
        decoder.getHeaderName(0) == 'Content-length'
        decoder.getHeaderName(1) == 'X-test'
        decoder.getHeader(0) == '0'
        decoder.getHeader(1) == 'abc'
        decoder.getBody() == ''

        cleanup:
        block.release()
        buffer.close()
    }

    def "should decode body"() {
        given:
        def buffer = TransportBuffer.create(256, 2)
        def encoder = new HttpRequestEncoder()
        def decoder = new HttpRequestDecoder()
        def ex = exchange('POST', '/users/42?active=true', 'b' + 'o'.repeat(127))
        ex.requestHeaders.add('Content-Length', '128')
        ex.requestBody

        def block = encoder.encode(ex, buffer)
        def reader = block.reader()

        when:
        decoder.parse(reader)

        then:
        decoder.method == HttpMethod.POST
        decoder.target == '/users/42?active=true'
        decoder.contentLength == 128
        !decoder.chunked
        decoder.getHeaderName(0) == 'Content-length'
        decoder.getHeader(0) == '128'
        decoder.getBody() == 'b' + 'o'.repeat(127)

        cleanup:
        block.release()
        buffer.close()
    }

    def "should detect chunked transfer encoding"() {
        given:
        def buffer = TransportBuffer.create(256, 2)
        def encoder = new HttpRequestEncoder()
        def decoder = new HttpRequestDecoder()

        when: 'supported multiple codes'
        def ex = exchange('GET', '/stream')
        ex.requestHeaders.add('Transfer-Encoding', 'gzip, chunked')
        def writer = encoder.encode(ex, buffer)
        def reader = writer.reader()

        and:
        decoder.parse(reader)

        then:
        decoder.method == HttpMethod.GET
        decoder.target == '/stream'
        decoder.chunked

        when: 'supported single code'
        writer.release()
        ex = exchange('GET', '/stream')
        ex.requestHeaders.add('Transfer-Encoding', 'chunked')
        writer = encoder.encode(ex, buffer)
        reader = writer.reader()

        and:
        decoder.parse(reader)

        then:
        decoder.method == HttpMethod.GET
        decoder.target == '/stream'
        decoder.chunked

        when: 'supported with OWS'
        writer.release()
        ex = exchange('GET', '/stream')
        ex.requestHeaders.add('Transfer-Encoding', ' chunked    ')
        writer = encoder.encode(ex, buffer)
        reader = writer.reader()

        and:
        decoder.parse(reader)

        then:
        decoder.method == HttpMethod.GET
        decoder.target == '/stream'
        decoder.chunked

        when: 'unsupported transfer encoding'
        writer.release()
        ex = exchange('GET', '/stream')
        ex.requestHeaders.add('Transfer-Encoding', 'chunked, gzip') // not valid in RFC 9112
        writer = encoder.encode(ex, buffer)
        reader = writer.reader()

        and:
        decoder.parse(reader)

        then:
        thrown(HttpRequestException)

        when: 'unsupported transfer encoding'
        writer.release()
        ex = exchange('GET', '/stream')
        ex.requestHeaders.add('Transfer-Encoding', 'gzip') // not valid in RFC 9112
        writer = encoder.encode(ex, buffer)
        reader = writer.reader()

        and:
        decoder.parse(reader)

        then:
        thrown(HttpRequestException)

        cleanup:
        writer.release()
        buffer.close()
    }
}
