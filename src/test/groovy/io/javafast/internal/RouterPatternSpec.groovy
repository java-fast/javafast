package io.javafast.internal


import spock.lang.Specification

class RouterPatternSpec extends Specification {

    def "should match exact route and reject suffix"() {
        given:
        def pattern = RoutePattern.compile("/users", false, false, false)

        expect:
        pattern.match("/users") != null
        pattern.match("/users/1") == null
    }

    def "should capture single path parameter"() {
        given:
        def pattern = RoutePattern.compile("/users/{id}", false, false, false)

        when:
        def match = pattern.match("/users/123")

        then:
        match != null
        match.pathParams().get("id") == "123"
    }

    def "should capture slash parameter"() {
        given:
        def pattern = RoutePattern.compile("/files/<path>", false, false, false)

        when:
        def match = pattern.match("/files/a/b/c")

        then:
        match != null
        match.pathParams().get("path") == "a/b/c"
    }

    def "should match global wildcard"() {
        given:
        def pattern = RoutePattern.compile("*", false, false, false)

        expect:
        pattern.match("/anything/here") != null
    }

    def "should match trailing wildcard under api path"() {
        given:
        def pattern = RoutePattern.compile("/api/<path>*", false, false, false)

        expect:
        pattern.match("/api/x") != null
        pattern.match("/api/v1/users") != null
        pattern.match("/apix") == null
    }

    def "should match case-insensitively when enabled"() {
        given:
        def pattern = RoutePattern.compile("/Users/{id}", false, false, true)

        expect:
        pattern.match("/users/ABC") != null
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
        def match = pattern.match("/file-report.txt")

        then:
        match != null
        match.pathParams().get("name") == "report"
    }

}
