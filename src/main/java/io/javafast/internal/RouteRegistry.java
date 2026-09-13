package io.javafast.internal;

import io.javafast.http.Handler;
import io.javafast.http.HandlerPhase;
import io.javafast.http.HttpMethod;
import io.javafast.router.HandlerWrapper;
import io.javafast.security.AccessController;

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
        add(method, path, handler, null);
    }

    void add(HttpMethod method, String path, Handler handler, AccessController controller) {
        add(RouteKey.http(method), path, handler, controller);
    }

    void add(HandlerPhase phase, String path, Handler handler) {
        add(RouteKey.phase(phase), path, handler, null);
    }

    private void add(RouteKey key, String path, Handler handler, AccessController controller) {
        if (handler == null) throw new NullPointerException("handler");
        final RoutePattern pattern = RoutePattern.compile(path, this.ignoreTrailingSlashes, this.multipleSlashes, this.caseInsensitive);
        final List<Route> entries = this.routes.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (key.phase() == null) {
            for (Route existing : entries) {
                if (existing.path().equals(path)) {
                    throw new IllegalArgumentException("Handler with method='" + key.method() + "' and path='" + path + "' already exists.");
                }
            }
        }
        final Handler effective = this.wrapper == null ? handler : this.wrapper.wrap(handler);
        entries.add(new Route(key.method(), key.phase(), path, effective, controller, pattern));
    }

    RouteMatch findFirst(HttpMethod method, String requestPath) {
        return findFirst(RouteKey.http(method), requestPath);
    }

    List<RouteMatch> findAll(HttpMethod method, String requestPath) {
        return findAll(RouteKey.http(method), requestPath);
    }

    RouteMatch findFirst(HandlerPhase phase, String requestPath) {
        return findFirst(RouteKey.phase(phase), requestPath);
    }

    List<RouteMatch> findAll(HandlerPhase phase, String requestPath) {
        return findAll(RouteKey.phase(phase), requestPath);
    }

    private RouteMatch findFirst(RouteKey key, String requestPath) {
        final List<Route> entries = this.routes.get(key);
        if (entries == null) return null;
        for (Route route : entries) {
            final PatternMatch match = route.pattern().match(requestPath);
            if (match != null) return new RouteMatch(route, match.pathParams());
        }
        return null;
    }

    private List<RouteMatch> findAll(RouteKey key, String requestPath) {
        final List<Route> entries = this.routes.get(key);
        if (entries == null) return List.of();
        final List<RouteMatch> matches = new ArrayList<>();
        for (Route route : entries) {
            final PatternMatch match = route.pattern().match(requestPath);
            if (match != null) matches.add(new RouteMatch(route, match.pathParams()));
        }
        return List.copyOf(matches);
    }

    boolean has(HttpMethod method, String requestPath) {
        return findFirst(method, requestPath) != null;
    }

}
