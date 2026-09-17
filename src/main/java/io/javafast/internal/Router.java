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
    private final RouterState routerState;

    Router() {
        this(false, false, false, null);
    }

    Router(boolean ignoreTrailingSlashes, boolean multipleSlashes, boolean caseInsensitive, HandlerWrapper wrapper) {
        this(new RouteRegistry(ignoreTrailingSlashes, multipleSlashes, caseInsensitive, wrapper));
    }

    Router(RouteRegistry registry) {
        this.registry = registry;
        this.routerState = new RouterState();
    }

    void handle(Context ctx) throws Exception {

        this.registry.findAll(this.routerState, HandlerPhase.BEFORE, ctx.path(), m -> {
            m.route().handler().handle(ctx);
            throwIfHandlersSkipped(ctx);
        });

        final RouteMatch match = find(ctx.method(), ctx.path());
        if (match == null) {
            throw HttpResponseException.notFound(ctx.method(), ctx.path());
        }

        this.registry.findAll(this.routerState, HandlerPhase.BEFORE_MATCHED, ctx.path(), m -> {
            // if beforeMatched has no path params, we inherit route's path params instead
            final var effective = m.pathParams().isEmpty() ? match.pathParams() : m.pathParams();
            ((ContextInternal) ctx).setPathParamMap(effective);
            m.route().handler().handle(ctx);
            throwIfHandlersSkipped(ctx);
        });

        ((ContextInternal) ctx).setPathParamMap(match.pathParams());
        match.route().handler().handle(ctx);
        if (skipRemainingHandlers(ctx)) return;

        this.registry.findAll(this.routerState, HandlerPhase.AFTER_MATCHED, ctx.path(), m -> {
            // if afterMatched has no path params, we inherit route's path params instead
            final var effective = m.pathParams().isEmpty() ? match.pathParams() : m.pathParams();
            ((ContextInternal) ctx).setPathParamMap(effective);
            m.route().handler().handle(ctx);
            throwIfHandlersSkipped(ctx);
        });

        handleAfter(ctx);
    }

    void handleAfter(Context ctx) throws Exception {
        this.registry.findAll(this.routerState, HandlerPhase.AFTER, ctx.path(), m -> {
            m.route().handler().handle(ctx);
            throwIfHandlersSkipped(ctx);
        });
    }

    private static boolean skipRemainingHandlers(Context ctx) {
        return ((ContextInternal)ctx).isSkipRemainingHandlers();
    }

    private static void throwIfHandlersSkipped(Context ctx) {
        if (skipRemainingHandlers(ctx)) throw SkipHandlersException.get();
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
        return this.registry.findFirst(this.routerState, method, requestPath);
    }

}
