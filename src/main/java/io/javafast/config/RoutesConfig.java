package io.javafast.config;

import io.javafast.http.ExceptionHandler;
import io.javafast.http.Handler;
import io.javafast.http.HandlerPhase;
import io.javafast.http.HttpMethod;
import io.javafast.internal.DefaultRouting;
import io.javafast.router.RoutingApi;
import io.javafast.security.AccessController;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class RoutesConfig implements RoutingApi, Lazy {

    private final List<Consumer<DefaultRouting>> configBuffer = new ArrayList<>();

    final RouterConfig routerConfig;
    DefaultRouting routing;

    public RoutesConfig(RouterConfig routerConfig) {
        this.routerConfig = routerConfig;
    }

    @Override
    public void init() {
        if (this.routing != null) {
            return;
        }

        this.routing = new DefaultRouting(
            this.routerConfig.contextPath,
            this.routerConfig.ignoreTrailingSlashes,
            this.routerConfig.treatMultipleSlashesAsSingleSlash,
            this.routerConfig.caseInsensitiveRoutes,
            this.routerConfig.handlerWrapper
        );

        this.configBuffer.forEach(operation -> operation.accept(this.routing));
        this.configBuffer.clear();
    }

    @Override
    public <E extends Exception> RoutingApi exception(Class<E> type, ExceptionHandler<? super E> handler) {
        return addConfig(routing -> routing.exception(type, handler));
    }

    @Override
    public RoutingApi error(int status, Handler handler) {
        return this.addConfig(routing -> routing.error(status, handler));
    }

    @Override
    public RoutingApi error(int status, String contentType, Handler handler) {
        return addConfig(routing -> routing.error(status, contentType, handler));
    }

    @Override
    public RoutingApi addHttpHandler(HttpMethod method, String path, Handler handler, AccessController controller) {
        return addConfig(routing -> routing.addHttpHandler(method, path, handler, controller));
    }

    @Override
    public RoutingApi addHandler(HandlerPhase phase, String path, Handler handler) {
        return addConfig(routing -> routing.addHandler(phase, path, handler));
    }

    private RoutingApi addConfig(Consumer<DefaultRouting> operation) {
        if (this.routing != null) {
            operation.accept(this.routing);
        } else {
            this.configBuffer.add(operation);
        }
        return this;
    }

}
