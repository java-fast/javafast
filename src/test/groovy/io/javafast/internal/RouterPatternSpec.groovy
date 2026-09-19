package io.javafast.internal


import spock.lang.Specification

class RouterPatternSpec extends Specification {

    RouteMatch state

    def setup() {
        state = new RouteMatch()
    }

    def "should match exact route and reject suffix"() {
        given:
        def pattern = RoutePattern.compile("/users", false, false, false)

        expect:
        pattern.match(state.patternMatch, "/users") != null
        pattern.match(state.patternMatch, "/users/1") == null
    }

    def "should capture single path parameter"() {
        given:
        def pattern = RoutePattern.compile("/users/{id}", false, false, false)

        when:
        def match = pattern.match(state.patternMatch, "/users/123")

        then:
        match != null
        match.get("id") == "123"
    }

    def "should capture nested path parameters"() {
        given:
        def pattern = RoutePattern.compile("/users/{id}/groups/{gid}/roles/{rid}", false, false, false)

        when:
        def match = pattern.match(state.patternMatch, "/users/123/groups/9876/roles/xx")

        then:
        match != null
        match.get("id") == "123"
        match.get("gid") == "9876"
        match.get("rid") == "xx"
        match.get() == ["id":"123", "gid":"9876", "rid":"xx"]
    }

    def "should capture slash parameter"() {
        given:
        def pattern = RoutePattern.compile("/files/<path>", false, false, false)

        when:
        def match = pattern.match(state.patternMatch, "/files/a/b/c")

        then:
        match != null
        match.get("path") == "a/b/c"
    }

    def "should match global wildcard"() {
        given:
        def pattern = RoutePattern.compile("*", false, false, false)

        expect:
        pattern.match(state.patternMatch, "/anything/here") != null
    }

    def "should match trailing wildcard under api path"() {
        given:
        def pattern = RoutePattern.compile("/api/<path>*", false, false, false)

        expect:
        pattern.match(state.patternMatch, "/api/x") != null
        pattern.match(state.patternMatch, "/api/v1/users") != null
        pattern.match(state.patternMatch, "/apix") == null
    }

    def "should match case-insensitively when enabled"() {
        given:
        def pattern = RoutePattern.compile("/Users/{id}", false, false, true)

        expect:
        pattern.match(state.patternMatch, "/users/ABC") != null
    }

    def "should reject duplicate parameter names"() {
        when:
        RoutePattern.compile("/users/{id}/orders/{id}", false, false, false)

        then:
        thrown(IllegalArgumentException)
    }

    def "should capture parameter in composite segment"() {
        given:
        def pattern = RoutePattern.compile("/file-{name}.txt", false, false, false)

        when:
        def match = pattern.match(state.patternMatch, "/file-report.txt")

        then:
        match != null
        match.get("name") == "report"
    }

}
