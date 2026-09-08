package io.javafast.internal.router;

import io.javafast.http.Handler;
import io.javafast.http.HttpMethod;

public final class RouterTest {
    static void main(String[] args) {
        Router router = new Router();
        Handler users = ctx -> {
        };
        router.add(HttpMethod.GET, "/users/{id}", users, null);

        var match = router.find(HttpMethod.GET, "/users/42");
        if (match == null) throw new AssertionError("route not found");
        if (match.route().handler() != users) throw new AssertionError("wrong handler");
        if (!"42".equals(match.pathParams().get("id"))) throw new AssertionError("wrong path param");
        if (router.find(HttpMethod.POST, "/users/42") != null) throw new AssertionError("wrong method matched");

        try {
            router.add(HttpMethod.GET, "/users/{id}", ctx -> {
            });
            throw new AssertionError("duplicate route accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        System.out.println("RouterTest: OK");
    }
}
