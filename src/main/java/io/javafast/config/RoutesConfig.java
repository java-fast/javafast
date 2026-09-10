package io.javafast.config;

import io.javafast.http.ExceptionHandler;
import io.javafast.http.Handler;
import io.javafast.http.HandlerPhase;
import io.javafast.http.HttpMethod;
import io.javafast.internal.DefaultRouting;
import io.javafast.router.RoutingApi;
import io.javafast.security.AccessController;

public final class RoutesConfig implements RoutingApi {

    final RouterConfig routerConfig;
    final DefaultRouting defaultRouting;

    public RoutesConfig(RouterConfig routerConfig) {
        this.routerConfig = routerConfig;
        this.defaultRouting = new DefaultRouting(false, false, false, null);
    }

    @Override
    public <E extends Exception> RoutingApi exception(Class<E> type, ExceptionHandler<? super E> handler) {
        return this.defaultRouting.exception(type, handler);
    }

    @Override
    public RoutingApi error(int status, Handler handler) {
        return this.defaultRouting.error(status, handler);
    }

    @Override
    public RoutingApi error(int status, String contentType, Handler handler) {
        return this.defaultRouting.error(status, contentType, handler);
    }

    @Override
    public RoutingApi addHttpHandler(HttpMethod method, String path, Handler handler, AccessController controller) {
        return this.defaultRouting.addHttpHandler(method, path, handler, controller);
    }

    @Override
    public RoutingApi addHandler(HandlerPhase phase, String path, Handler handler) {
        return this.defaultRouting.addHandler(phase, path, handler);
    }
}
