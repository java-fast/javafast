package io.javafast.internal

import io.javafast.config.AppDataManager
import io.javafast.http.HttpStatus
import io.javafast.test.TestUtils
import io.javafast.transport.HttpTestRequestEncoder
import io.javafast.transport.TransportBuffer
import spock.lang.Specification

class DefaultHttpRequestDispatcherSpec extends Specification implements TestUtils {

    TransportBuffer buffer
    HttpTestRequestEncoder encoder

    def setup() {
        this.buffer = TransportBuffer.create(64, 1)
        this.encoder = new HttpTestRequestEncoder()
    }

    def "should create ContextInternal per request and write successful response"() {
        given:
        def router = new DefaultRouting()
        router.get('/users/{id}') { ctx ->
            ctx.result(ctx.pathParam('id'))
        }

        def requestPool = RequestContextPool.create(1)
        def executor = new DefaultHttpRequestDispatcher(requestPool, router, new AppDataManager())
        def exchange = exchange('GET', '/users/42')
        def writer = this.encoder.encode(exchange, this.buffer)

        when:
        executor.dispatch(writer.reader())

        then:
        verifyAll(requestPool.get()) {
            statusCode == 200
            path() == '/users/42'
            result() == '42'
        }

        cleanup:
        writer.release()
    }

    def "should custom contextPath including all normalize cases work"() {
        given:
        def router = new DefaultRouting(contextPath)
        router.get('/users/{id}') { ctx ->
            ctx.result(ctx.pathParam('id'))
        }
        def requestPool = RequestContextPool.create(1)
        def executor = new DefaultHttpRequestDispatcher(requestPool, router, new AppDataManager())
        def exchange = exchange('GET', requestPath)
        def writer = this.encoder.encode(exchange, this.buffer)

        when:
        executor.dispatch(writer.reader())

        then:
        verifyAll(requestPool.get()) {
            statusCode == 200
            result() == '42'
        }

        where:
        contextPath | requestPath
        null        | '/users/42'
        ''          | '/users/42'
        '   '       | '/users/42'
        '/'         | '/users/42'
        'api'       | '/api/users/42'
        '/api'      | '/api/users/42'
        '/api/'     | '/api/users/42'
        'api/'      | '/api/users/42'
        '/api///'   | '/api/users/42'
        'api///'    | '/api/users/42'
    }

    def "should map not found to exception handler and return 404"() {
        given:
        def requestPool = RequestContextPool.create(1)
        def executor = new DefaultHttpRequestDispatcher(requestPool, new DefaultRouting(), new AppDataManager())
        def exchange = exchange('GET', '/missing')
        def writer = this.encoder.encode(exchange, this.buffer)

        when:
        executor.dispatch(writer.reader())

        then:
        verifyAll(requestPool.get()) {
            statusCode == HttpStatus.NOT_FOUND.code()
            path() == '/missing'
            result() == 'Endpoint GET /missing not found'
        }
    }

}
