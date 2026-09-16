package io.javafast.internal;

import io.javafast.http.*;
import io.javafast.router.HandlerWrapper;
import io.javafast.router.RoutingApi;
import io.javafast.router.AccessController;
import io.javafast.util.Util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of the public RoutingApi.
 */
public final class DefaultRouting implements RoutingApi {

    private static final Logger log = Logger.getLogger(DefaultRouting.class.getName());

    final String contextPath;
    final Router router;
    final ErrorMapper errors = new ErrorMapper();
    final ExceptionMapper exceptions = new ExceptionMapper();

    public DefaultRouting() {
        this(null, false, false, false, null);
    }

    public DefaultRouting(String contextPath) {
        this(contextPath, false, false, false, null);
    }

    public DefaultRouting(boolean ignoreTrailingSlashes, boolean multipleSlashes, boolean caseInsensitive, HandlerWrapper wrapper) {
        this(null, ignoreTrailingSlashes, multipleSlashes, caseInsensitive, wrapper);
    }

    public DefaultRouting(String contextPath, boolean ignoreTrailingSlashes, boolean multipleSlashes, boolean caseInsensitive, HandlerWrapper wrapper) {
        this.contextPath = Util.normalizeContextPath(contextPath);
        this.router = new Router(ignoreTrailingSlashes, multipleSlashes, caseInsensitive, wrapper);
    }

    @Override
    public <E extends Exception> RoutingApi exception(Class<E> type, ExceptionHandler<? super E> handler) {
        this.exceptions.add(type, handler);
        return this;
    }

    @Override
    public RoutingApi error(int status, Handler handler) {
        return error(status, "*", handler);
    }

    @Override
    public RoutingApi error(int status, String contentType, Handler handler) {
        this.errors.add(status, contentType, handler);
        return this;
    }

    @Override
    public RoutingApi addHttpHandler(HttpMethod method, String path, Handler handler, AccessController controller) {
        this.router.add(method, Util.prefixContextPath(this.contextPath, path), handler, controller);
        return this;
    }

    @Override
    public RoutingApi addHandler(HandlerPhase phase, String path, Handler handler) {
        this.router.add(phase, Util.prefixContextPath(this.contextPath, path), handler);
        return this;
    }

    public void handle(Context context) throws Exception {
        ((ContextInternal) context).setContextPath(this.contextPath);
        this.router.handle(context);
    }

    public boolean handleException(Context context, Throwable throwable) {
        try {
            final boolean handle = this.exceptions.handle(context, throwable);
            if (Router.skipRemainingHandlers(context)) return handle;
            // AFTER phase should run in an exception case as well
            for (RouteMatch after : this.router.findAll(HandlerPhase.AFTER, context.path())) {
                after.route().handler().handle(context);
            }
            return handle;
        } catch (Exception e) {
            log.log(Level.WARNING, "Exception swallowed due to another exception in AFTER phase", throwable);
            return this.exceptions.handle(context, e);
        }
    }

    public void handleError(int status, Context context) throws Exception {
        this.errors.handle(status, context);
    }

}
