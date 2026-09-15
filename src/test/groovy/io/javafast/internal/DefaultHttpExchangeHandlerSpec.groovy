package io.javafast.internal

import io.javafast.config.AppDataManager
import io.javafast.http.HttpStatus
import io.javafast.test.TestUtils
import spock.lang.Specification

class DefaultHttpExchangeHandlerSpec extends Specification implements TestUtils {

    def "should create ContextInternal per request and write successful response"() {
        given:
        def router = new DefaultRouting()
        router.get('/users/{id}') { ctx ->
            ctx.result(ctx.pathParam('id'))
        }
        def executor = new DefaultHttpExchangeHandler(router, new AppDataManager())
        def exchange = exchange('GET', '/users/42')

        when:
        executor.handle(exchange)

        then:
        exchange.statusCode == 200
        exchange.responseText() == '42'
        exchange.closed
    }

    def "should custom contextPath including all normalize cases work"() {
        given:
        def router = new DefaultRouting(contextPath)
        router.get('/users/{id}') { ctx ->
            ctx.result(ctx.pathParam('id'))
        }
        def executor = new DefaultHttpExchangeHandler(router, new AppDataManager())
        def exchange = exchange('GET', requestPath)

        when:
        executor.handle(exchange)

        then:
        exchange.statusCode == 200
        exchange.responseText() == '42'
        exchange.closed

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
        def executor = new DefaultHttpExchangeHandler(new DefaultRouting(), new AppDataManager())
        def exchange = exchange('GET', '/missing')

        when:
        executor.handle(exchange)

        then:
        exchange.statusCode == HttpStatus.NOT_FOUND.code()
        exchange.responseText() == HttpStatus.NOT_FOUND.toString()
        exchange.closed
    }

}
