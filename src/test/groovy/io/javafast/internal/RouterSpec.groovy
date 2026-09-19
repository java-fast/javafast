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
        match.patternMatch.params.get("id") == "42"

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

    def 'HEAD requests match GET route'() {
        given:
        def router = new Router()
        router.add(HttpMethod.GET, '/hello') {it.result('hello') }

        expect:
        router.find(HttpMethod.HEAD, '/hello') != null
    }

    def 'duplicate explicit HEAD mapping should override'() {
        given:
        def router = new Router()
        def headContext = contextOf(HttpMethod.HEAD, '/hello')

        when:
        router.add(HttpMethod.HEAD, '/hello') { it.result('head') }
        router.add(HttpMethod.HEAD, '/hello') { it.result('another') }
        router.find(HttpMethod.HEAD, '/hello').route().handler().handle(headContext)

        then:
        noExceptionThrown()
        headContext.result() == 'another'
    }

    def 'explicit HEAD mapping should override implicit HEAD mapping regardless of registration order'() {
        given:
        def router = new Router()
        def headContext = contextOf(HttpMethod.HEAD, '/hello')
        def getContext = contextOf(HttpMethod.GET, '/hello')

        when: 'adding HEAD first'
        router.add(HttpMethod.HEAD, '/hello') {it.result('head')}
        router.add(HttpMethod.GET, '/hello') { it.result('hello') }
        // execute handlers
        router.handle(headContext)
        router.handle(getContext)

        then:
        noExceptionThrown()
        headContext.result() == 'head'
        getContext.result() == 'hello'

        when: 'adding GET first'
        router = new Router()
        router.add(HttpMethod.GET, '/hello') { it.result('hello') }
        router.add(HttpMethod.HEAD, '/hello') { it.result('head') }
        // execute handlers
        router.handle(getContext)
        router.handle(headContext)

        then:
        noExceptionThrown()
        getContext.result() == 'hello'
        headContext.result() == 'head'
    }

}
