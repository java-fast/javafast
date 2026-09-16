package io.javafast.internal

import io.javafast.http.*
import io.javafast.router.AccessController
import io.javafast.test.TestUtils
import spock.lang.Specification

class RouterSpec extends Specification implements TestUtils {

    def "should find route by method and capture path params"() {
        given:
        def router = new Router()
        Handler users = { ctx -> }
        router.add(HttpMethod.GET, "/users/{id}", users, null)

        when:
        def match = router.find(HttpMethod.GET, "/users/42")

        then:
        match != null
        match.route().handler() == users
        match.pathParams().get("id") == "42"

        and:
        router.find(HttpMethod.POST, "/users/42") == null
    }

    def "should reject duplicate route definition"() {
        given:
        def router = new Router()
        router.add(HttpMethod.GET, "/users/{id}", { ctx -> }, null)

        when:
        router.add(HttpMethod.GET, "/users/{id}", { ctx -> })

        then:
        thrown(IllegalArgumentException)
    }

    def "should throw not found when no route matches in handle"() {
        given:
        def router = new Router()
        Context ctx = contextOf(HttpMethod.GET, '/missing')

        when:
        router.handle(ctx)

        then:
        def ex = thrown(HttpResponseException)
        ex.status() == HttpStatus.NOT_FOUND
    }

    def "should throw forbidden when access controller denies request"() {
        given:
        def router = new Router()
        boolean handlerCalled = false
        Handler handler = { ignored -> handlerCalled = true }
        AccessController denyAll = { ignored -> false }
        router.add(HttpMethod.GET, '/users/{id}', handler, denyAll)
        Context ctx = contextOf(HttpMethod.GET, '/users/42')

        when:
        router.handle(ctx)

        then:
        def ex = thrown(HttpResponseException)
        ex.status() == HttpStatus.FORBIDDEN
        !handlerCalled
    }

    def "should execute matched handler when route is found and allowed"() {
        given:
        def router = new Router()
        boolean handlerCalled = false
        Handler handler = { ignored -> handlerCalled = true }
        router.add(HttpMethod.GET, '/users/{id}', handler, null)
        Context ctx = contextOf(HttpMethod.GET, '/users/42')

        when:
        router.handle(ctx)

        then:
        handlerCalled
    }
}
