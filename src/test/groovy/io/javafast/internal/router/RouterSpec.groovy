package io.javafast.internal.router

import io.javafast.http.Handler
import io.javafast.http.HttpMethod
import spock.lang.Specification

class RouterSpec extends Specification {

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
}
