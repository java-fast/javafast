package io.javafast.transport

import io.javafast.http.HttpMethod
import io.javafast.test.TestUtils
import spock.lang.Ignore
import spock.lang.Specification

class HttpRequestDecoderSpec extends Specification implements TestUtils {

    private static Object fieldValue(Object target, String fieldName) {
        def field = target.class.getDeclaredField(fieldName)
        field.accessible = true
        field.get(target)
    }

    def "should decode method target and headers from http1.1 wire format"() {
        given:
        def buffer = TransportBuffer.create(256, 2)
        def encoder = new HttpRequestEncoder()
        def decoder = new HttpRequestDecoder()
        def exchange = exchange('POST', '/users/42?active=true')
        exchange.requestHeaders.add('Content-Length', '0')
        exchange.requestHeaders.add('X-Test', 'abc')
        def block = encoder.encode(exchange, buffer)
        def reader = block.reader()

        when:
        decoder.parse(reader)

        then:
        decoder.method == HttpMethod.POST
        decoder.target == '/users/42?active=true'
//        fieldValue(decoder, 'contentLength') == 0L
//        !fieldValue(decoder, 'chunked')
//        fieldValue(decoder, 'headersStart') >= 0
//        fieldValue(decoder, 'headersEnd') >= fieldValue(decoder, 'headersStart')
//        fieldValue(decoder, 'bodyStart') == fieldValue(decoder, 'bodyEnd') + 1

        cleanup:
        block.release()
        buffer.close()
    }

    @Ignore
    def "should detect chunked transfer encoding"() {
        given:
        def buffer = TransportBuffer.create(256, 2)
        def encoder = new HttpRequestEncoder()
        def decoder = new HttpRequestDecoder()
        def exchange = exchange('GET', '/stream')
        exchange.requestHeaders.add('Transfer-Encoding', 'gzip, chunked')
        def block = encoder.encode(exchange, buffer)
        def reader = block.reader()

        when:
        decoder.parse(reader)

        then:
        decoder.method == HttpMethod.GET
        decoder.target == '/stream'
        fieldValue(decoder, 'chunked')

        cleanup:
        block.release()
        buffer.close()
    }
}
