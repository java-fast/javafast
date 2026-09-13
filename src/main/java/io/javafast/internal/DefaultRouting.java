package io.javafast.internal;

import io.javafast.http.ExceptionHandler;
import io.javafast.http.Handler;
import io.javafast.http.HandlerPhase;
import io.javafast.http.HttpMethod;
import io.javafast.router.HandlerWrapper;
import io.javafast.router.RoutingApi;
import io.javafast.security.AccessController;

/**
 * Internal implementation of the public RoutingApi.
 */
public final class DefaultRouting implements RoutingApi {

    final Router router;
    final ErrorMapper errors = new ErrorMapper();
    final ExceptionMapper exceptions = new ExceptionMapper();

    public DefaultRouting() {
        this(false, false, false, null);
    }

    public DefaultRouting(boolean ignoreTrailingSlashes, boolean multipleSlashes, boolean caseInsensitive, HandlerWrapper wrapper) {
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
        this.router.add(method, path, handler, controller);
        return this;
    }

    @Override
    public RoutingApi addHandler(HandlerPhase phase, String path, Handler handler) {
        this.router.add(phase, path, handler);
        return this;
    }

}
