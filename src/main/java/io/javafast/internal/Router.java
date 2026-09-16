package io.javafast.internal;

import io.javafast.http.*;
import io.javafast.router.HandlerWrapper;
import io.javafast.security.AccessController;

import java.util.List;

/**
 * Internal routing engine.
 */
final class Router {

    private final RouteRegistry registry;

    Router() {
        this(false, false, false, null);
    }

    Router(boolean ignoreTrailingSlashes, boolean multipleSlashes,
                  boolean caseInsensitive, HandlerWrapper wrapper) {
        this(new RouteRegistry(ignoreTrailingSlashes, multipleSlashes, caseInsensitive, wrapper));
    }

    Router(RouteRegistry registry) {
        this.registry = registry;
    }

    void handle(Context ctx) throws Exception {
        for (RouteMatch before : findAll(HandlerPhase.BEFORE, ctx.path())) {
            before.route().handler().handle(ctx);
        }

        final RouteMatch match = find(ctx.method(), ctx.path());
        if (match == null) {
            throw HttpResponseException.notFound(ctx.method(), ctx.path());
        }

        ((ContextInternal) ctx).setPathParamMap(match.pathParams());

        for (RouteMatch beforeMatched : findAll(HandlerPhase.BEFORE_MATCHED, ctx.path())) {
            beforeMatched.route().handler().handle(ctx);
        }

        final AccessController access = match.route().accessController();
        if (access != null && !access.isAllowed(ctx)) {
            throw new HttpResponseException(HttpStatus.FORBIDDEN);
        }
        match.route().handler().handle(ctx);

        for (RouteMatch afterMatched : findAll(HandlerPhase.AFTER_MATCHED, ctx.path())) {
            afterMatched.route().handler().handle(ctx);
        }

        for (RouteMatch after : findAll(HandlerPhase.AFTER, ctx.path())) {
            after.route().handler().handle(ctx);
        }
    }

    void add(HttpMethod method, String path, Handler handler) {
        this.registry.add(method, path, handler, null);
    }

    void add(HttpMethod method, String path, Handler handler, AccessController controller) {
        this.registry.add(method, path, handler, controller);
    }

    void add(HandlerPhase phase, String path, Handler handler) {
        this.registry.add(phase, path, handler);
    }

    RouteMatch find(HttpMethod method, String requestPath) {
        return this.registry.findFirst(method, requestPath);
    }

    List<RouteMatch> findAll(HandlerPhase phase, String requestPath) {
        return this.registry.findAll(phase, requestPath);
    }

    boolean has(HttpMethod method, String requestPath) {
        return this.registry.has(method, requestPath);
    }
}
