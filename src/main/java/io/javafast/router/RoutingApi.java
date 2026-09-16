package io.javafast.router;

import io.javafast.http.ExceptionHandler;
import io.javafast.http.Handler;
import io.javafast.http.HandlerPhase;
import io.javafast.http.HttpMethod;
import io.javafast.security.AccessController;

/**
 * Public routing API. The route representation and matcher remain implementation details.
 */
public interface RoutingApi {

    <E extends Exception> RoutingApi exception(Class<E> type, ExceptionHandler<? super E> handler);

    RoutingApi error(int status, Handler handler);

    RoutingApi error(int status, String contentType, Handler handler);

    RoutingApi addHttpHandler(HttpMethod method, String path, Handler handler, AccessController controller);

    default RoutingApi addHttpHandler(HttpMethod method, String path, Handler handler) {
        return addHttpHandler(method, path, handler, null);
    }

    RoutingApi addHandler(HandlerPhase phase, String path, Handler handler);

    default RoutingApi get(String path, Handler handler) {
        return addHttpHandler(HttpMethod.GET, path, handler);
    }

    default RoutingApi post(String path, Handler handler) {
        return addHttpHandler(HttpMethod.POST, path, handler);
    }

    default RoutingApi put(String path, Handler handler) {
        return addHttpHandler(HttpMethod.PUT, path, handler);
    }

    default RoutingApi patch(String path, Handler handler) {
        return addHttpHandler(HttpMethod.PATCH, path, handler);
    }

    default RoutingApi delete(String path, Handler handler) {
        return addHttpHandler(HttpMethod.DELETE, path, handler);
    }

    default RoutingApi head(String path, Handler handler) {
        return addHttpHandler(HttpMethod.HEAD, path, handler);
    }

    default RoutingApi options(String path, Handler handler) {
        return addHttpHandler(HttpMethod.OPTIONS, path, handler);
    }

    default RoutingApi trace(String path, Handler handler) {
        return addHttpHandler(HttpMethod.TRACE, path, handler);
    }

    default RoutingApi connect(String path, Handler handler) {
        return addHttpHandler(HttpMethod.CONNECT, path, handler);
    }

    default RoutingApi before(String path, Handler handler) {
        return addHandler(HandlerPhase.BEFORE, path, handler);
    }

    default RoutingApi before(Handler handler) {
        return before("*", handler);
    }

    default RoutingApi beforeMatched(String path, Handler handler) {
        return addHandler(HandlerPhase.BEFORE_MATCHED, path, handler);
    }

    default RoutingApi beforeMatched(Handler handler) {
        return beforeMatched("*", handler);
    }

    default RoutingApi afterMatched(String path, Handler handler) {
        return addHandler(HandlerPhase.AFTER_MATCHED, path, handler);
    }

    default RoutingApi afterMatched(Handler handler) {
        return afterMatched("*", handler);
    }

    default RoutingApi after(String path, Handler handler) {
        return addHandler(HandlerPhase.AFTER, path, handler);
    }

    default RoutingApi after(Handler handler) {
        return after("*", handler);
    }
}
