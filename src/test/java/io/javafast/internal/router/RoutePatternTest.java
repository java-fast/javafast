package io.javafast.internal.router;

public final class RoutePatternTest {

    static void main(String[] args) {
        exact();
        parameter();
        slashParameter();
        wildcard();
        trailingWildcard();
        caseInsensitive();
        duplicateParameters();
        composite();
        System.out.println("RoutePatternTest: OK");
    }

    private static void exact() {
        RoutePattern p = RoutePattern.compile("/users", false, false, false);
        check(p.match("/users") != null, "exact match");
        check(p.match("/users/1") == null, "exact no suffix");
    }

    private static void parameter() {
        RoutePattern p = RoutePattern.compile("/users/{id}", false, false, false);
        var m = p.match("/users/123");
        check(m != null && "123".equals(m.pathParams().get("id")), "parameter");
    }

    private static void slashParameter() {
        RoutePattern p = RoutePattern.compile("/files/<path>", false, false, false);
        var m = p.match("/files/a/b/c");
        check(m != null && "a/b/c".equals(m.pathParams().get("path")), "slash parameter");
    }

    private static void wildcard() {
        RoutePattern p = RoutePattern.compile("*", false, false, false);
        check(p.match("/anything/here") != null, "wildcard");
    }

    private static void trailingWildcard() {
        RoutePattern p = RoutePattern.compile("/api/<path>*", false, false, false);
        check(p.match("/api/x") != null, "trailing wildcard root");
        check(p.match("/api/v1/users") != null, "trailing wildcard suffix");
        check(p.match("/other") == null, "trailing wildcard mismatch");
    }

    private static void caseInsensitive() {
        RoutePattern p = RoutePattern.compile("/Users/{id}", false, false, true);
        check(p.match("/users/ABC") != null, "case insensitive");
    }

    private static void duplicateParameters() {
        try {
            RoutePattern.compile("/users/{id}/orders/{id}", false, false, false);
            throw new AssertionError("duplicate parameter accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void composite() {
        RoutePattern p = RoutePattern.compile("/file-{name}.txt", false, false, false);
        var m = p.match("/file-report.txt");
        check(m != null && "report".equals(m.pathParams().get("name")), "composite parameter");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError("failed: " + name);
    }
}
