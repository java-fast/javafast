package io.javafast.internal;

import io.javafast.http.Handler;
import io.javafast.http.HandlerPhase;
import io.javafast.http.HttpMethod;
import io.javafast.router.HandlerWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal route registry. Registration order is preserved for matching semantics.
 */
final class RouteRegistry {

    private final Map<RouteKey, List<Route>> routes = new HashMap<>();
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

    void add(HttpMethod method, String path, Handler handler) {
        add(RouteKey.http(method), path, handler);
    }

    void add(HandlerPhase phase, String path, Handler handler) {
        add(RouteKey.phase(phase), path, handler);
    }

    private void add(RouteKey key, String path, Handler handler) {
        if (handler == null) throw new NullPointerException("handler");
        final RoutePattern pattern = RoutePattern.compile(path, this.ignoreTrailingSlashes, this.multipleSlashes, this.caseInsensitive);
        final List<Route> entries = this.routes.computeIfAbsent(key, _ -> new ArrayList<>());
        if (key.phase() == null) {
            for (Route existing : entries) {
                if (existing.path().equals(path)) {
                    throw new IllegalArgumentException("Handler with method='" + key.method() + "' and path='" + path + "' already exists.");
                }
            }
        }
        final Handler effective = this.wrapper == null ? handler : this.wrapper.wrap(handler);
        entries.add(new Route(key.method(), key.phase(), path, effective, pattern));
    }

    /**
     * Replaces or adds a handler.
     */
    void replace(HttpMethod method, String path, Handler handler) {
        final RouteKey key = RouteKey.http(method);
        final List<Route> entries = this.routes.get(key);
        if (entries == null) {
            this.routes.computeIfAbsent(key, _ -> new ArrayList<>())
                .add(new Route(key.method(), key.phase(), path, handler, RoutePattern.compile(path, this.ignoreTrailingSlashes, this.multipleSlashes, this.caseInsensitive)));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            final Route route = entries.get(i);
            if (route.path().equals(path)) {
                entries.set(i, new Route(key.method(), key.phase(), path, handler, route.pattern()));
                return;
            }
        }
        entries.add(new Route(key.method(), key.phase(), path, handler, RoutePattern.compile(path, this.ignoreTrailingSlashes, this.multipleSlashes, this.caseInsensitive)));
    }

    RouteMatch findFirst(HttpMethod method, String requestPath) {
        return findFirst(RouteKey.http(method), requestPath);
    }

    List<RouteMatch> findAll(HandlerPhase phase, String requestPath) {
        return findAll(RouteKey.phase(phase), requestPath);
    }

    private RouteMatch findFirst(RouteKey key, String requestPath) {
        final List<Route> entries = this.routes.get(key);
        if (entries == null) return null;
        for (Route route : entries) {
            final PathParams match = route.pattern().match(requestPath);
            if (match != null) return new RouteMatch(route, match);
        }
        return null;
    }

    private List<RouteMatch> findAll(RouteKey key, String requestPath) {
        final List<Route> entries = this.routes.get(key);
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
        final List<Route> entries = this.routes.get(RouteKey.http(method));
        if (entries == null) return false;
        for (Route existing : entries) {
            if (existing.path().equals(requestPath)) {
                return true;
            }
        }
        return false;
    }

}
