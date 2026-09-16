package io.javafast.internal;

import io.javafast.http.*;
import io.javafast.router.HandlerWrapper;
import io.javafast.router.AccessController;

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
            if (skipRemainingHandlers(ctx)) return;
        }

        final RouteMatch match = find(ctx.method(), ctx.path());
        if (match == null) {
            throw HttpResponseException.notFound(ctx.method(), ctx.path());
        }

        for (RouteMatch beforeMatched : findAll(HandlerPhase.BEFORE_MATCHED, ctx.path())) {
            // if beforeMatched has no path params, we inherit route's path params instead
            final var effective = beforeMatched.pathParams().isEmpty() ? match.pathParams() : beforeMatched.pathParams();
            ((ContextInternal) ctx).setPathParamMap(effective);
            beforeMatched.route().handler().handle(ctx);
            if (skipRemainingHandlers(ctx)) return;
        }

        ((ContextInternal) ctx).setPathParamMap(match.pathParams());
        match.route().handler().handle(ctx);
        if (skipRemainingHandlers(ctx)) return;

        for (RouteMatch afterMatched : findAll(HandlerPhase.AFTER_MATCHED, ctx.path())) {
            ((ContextInternal) ctx).setPathParamMap(afterMatched.pathParams());
            afterMatched.route().handler().handle(ctx);
        }

        for (RouteMatch after : findAll(HandlerPhase.AFTER, ctx.path())) {
            after.route().handler().handle(ctx);
        }
    }

    static boolean skipRemainingHandlers(Context ctx) {
        return ((ContextInternal)ctx).isSkipRemainingHandlers();
    }

    void add(HttpMethod method, String path, Handler handler) {
        if (method == HttpMethod.HEAD) { // explicit HEAD overrides implicit HEAD if any
            this.registry.replace(HttpMethod.HEAD, path, handler);
            return;
        }
        this.registry.add(method, path, handler);
        if (method == HttpMethod.GET && !this.registry.has(HttpMethod.HEAD, path)) { // implicit HEAD should not override explicit HEAD
            this.registry.add(HttpMethod.HEAD, path, handler); // HEAD requests should match GET routes
        }
    }

    void add(HttpMethod method, String path, Handler handler, AccessController controller) {
        add(method, path, handler);
        if (controller != null) { // add a preconfigured BEFORE_MATCHED to run the given AccessController
            this.registry.add(HandlerPhase.BEFORE_MATCHED, path, ctx -> {
                if (!controller.isAllowed(ctx)) {
                    throw new HttpResponseException(HttpStatus.FORBIDDEN);
                }
            });
        }
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

}
