package io.javafast.internal;

import io.javafast.http.Handler;
import io.javafast.http.HandlerPhase;
import io.javafast.http.HttpMethod;
import io.javafast.router.HandlerWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal route registry. Registration order is preserved for matching semantics.
 */
final class RouteRegistry {

    private static final int INITIAL_CAPACITY = Integer.getInteger("javafast.http.methods.max.count", 16) + HandlerPhase.values().length;
    private final List<Route>[] routes = new ArrayList[INITIAL_CAPACITY];

    private final boolean ignoreTrailingSlashes;
    private final boolean multipleSlashes;
    private final boolean caseInsensitive;
    private final HandlerWrapper wrapper;

    RouteRegistry(boolean ignoreTrailingSlashes, boolean multipleSlashes, boolean caseInsensitive, HandlerWrapper wrapper) {
        this.ignoreTrailingSlashes = ignoreTrailingSlashes;
        this.multipleSlashes = multipleSlashes;
        this.caseInsensitive = caseInsensitive;
        this.wrapper = wrapper;
    }

    private List<Route> getRoutes(int key) {
        List<Route> routes = this.routes[key];
        if (routes == null) {
            this.routes[key] = (routes = new ArrayList<>());
        }
        return routes;
    }

    void add(HttpMethod method, String path, Handler handler) {
        add(method, null, path, handler);
    }

    void add(HandlerPhase phase, String path, Handler handler) {
        add(null, phase, path, handler);
    }

    private void add(HttpMethod method, HandlerPhase phase, String path, Handler handler) {
        if (handler == null) throw new NullPointerException("handler");
        final RoutePattern pattern = RoutePattern.compile(path, this.ignoreTrailingSlashes, this.multipleSlashes, this.caseInsensitive);
        final int key = method == null ? phase.key() : method.key();
        final List<Route> entries = getRoutes(key);
        if (phase == null) {
            for (Route existing : entries) {
                if (existing.path().equals(path)) {
                    throw new IllegalArgumentException("Handler with method='" + method + "' and path='" + path + "' already exists.");
                }
            }
        }
        final Handler effective = this.wrapper == null ? handler : this.wrapper.wrap(handler);
        entries.add(new Route(method, phase, path, effective, pattern));
    }

    /**
     * Replaces or adds a handler.
     */
    void replace(HttpMethod method, String path, Handler handler) {
        final int key = method.key();
        final List<Route> entries = this.routes[key];
        if (entries == null) {
            getRoutes(key)
                .add(new Route(method, null, path, handler, RoutePattern.compile(path, this.ignoreTrailingSlashes, this.multipleSlashes, this.caseInsensitive)));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            final Route route = entries.get(i);
            if (route.path().equals(path)) {
                entries.set(i, new Route(method, null, path, handler, route.pattern()));
                return;
            }
        }
        entries.add(new Route(method, null, path, handler, RoutePattern.compile(path, this.ignoreTrailingSlashes, this.multipleSlashes, this.caseInsensitive)));
    }

    RouteMatch findFirst(HttpMethod method, String requestPath) {
        return findFirst(method.key(), requestPath);
    }

    List<RouteMatch> findAll(HandlerPhase phase, String requestPath) {
        return findAll(phase.key(), requestPath);
    }

    private RouteMatch findFirst(int key, String requestPath) {
        final List<Route> entries = this.routes[key];
        if (entries == null) return null;
        for (Route route : entries) {
            final PathParams match = route.pattern().match(requestPath);
            if (match != null) return new RouteMatch(route, match);
        }
        return null;
    }

    private List<RouteMatch> findAll(int key, String requestPath) {
        final List<Route> entries = this.routes[key];
        if (entries == null) return List.of();
        final List<RouteMatch> matches = new ArrayList<>();
        for (Route route : entries) {
            final PathParams match = route.pattern().match(requestPath);
            if (match != null) matches.add(new RouteMatch(route, match));
        }
        return matches;
    }

    /**
     * Checks if the registry contains a route for the given method and request path without PatterMatch semantics
     */
    boolean has(HttpMethod method, String requestPath) {
        final int key = method.key();
        if (key >= this.routes.length) {
            return false;
        }
        final List<Route> entries = this.routes[key];
        if (entries == null) return false;
        for (Route existing : entries) {
            if (existing.path().equals(requestPath)) {
                return true;
            }
        }
        return false;
    }

}
