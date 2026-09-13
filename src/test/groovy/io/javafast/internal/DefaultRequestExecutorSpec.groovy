package io.javafast.internal

import io.javafast.http.HttpMethod
import io.javafast.http.HttpStatus
import io.javafast.test.TestUtils
import spock.lang.Specification

class DefaultRequestExecutorSpec extends Specification {

    def "should create ContextInternal per request and write successful response"() {
        given:
        def router = new Router()
        router.add(HttpMethod.GET, "/users/{id}") { ctx ->
            ctx.result(ctx.pathParam("id"))
        }
        def executor = new DefaultRequestExecutor(router)
        def exchange = TestUtils.exchange("GET", "/users/42")

        when:
        executor.handle(exchange)

        then:
        exchange.statusCode == 200
        exchange.responseText() == "42"
        exchange.closed
    }

    def "should map not found to exception handler and return 404"() {
        given:
        def router = new Router()
        def executor = new DefaultRequestExecutor(router)
        def exchange = TestUtils.exchange("GET", "/missing")

        when:
        executor.handle(exchange)

        then:
        exchange.statusCode == HttpStatus.NOT_FOUND.code()
        exchange.responseText() == HttpStatus.NOT_FOUND.toString()
        exchange.closed
    }

}
