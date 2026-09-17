package io.javafast.internal;

import io.javafast.http.HandlerPhase;
import io.javafast.http.HttpMethod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

record RouteKey(HttpMethod method, HandlerPhase phase) {

    private static final Map<HttpMethod, RouteKey> HTTP_KEYS = new ConcurrentHashMap<>();

    static { // pre-populate known HttpMethods
        HTTP_KEYS.put(HttpMethod.GET, new RouteKey(HttpMethod.GET, null));
        HTTP_KEYS.put(HttpMethod.POST, new RouteKey(HttpMethod.POST, null));
        HTTP_KEYS.put(HttpMethod.PUT, new RouteKey(HttpMethod.PUT, null));
        HTTP_KEYS.put(HttpMethod.DELETE, new RouteKey(HttpMethod.DELETE, null));
        HTTP_KEYS.put(HttpMethod.PATCH, new RouteKey(HttpMethod.PATCH, null));
        HTTP_KEYS.put(HttpMethod.HEAD, new RouteKey(HttpMethod.HEAD, null));
        HTTP_KEYS.put(HttpMethod.OPTIONS, new RouteKey(HttpMethod.OPTIONS, null));
        HTTP_KEYS.put(HttpMethod.TRACE, new RouteKey(HttpMethod.TRACE, null));
        HTTP_KEYS.put(HttpMethod.CONNECT, new RouteKey(HttpMethod.CONNECT, null));
    }

    private static final Map<HandlerPhase, RouteKey> PHASE_KEYS = Map.of(
        HandlerPhase.BEFORE, new RouteKey(null, HandlerPhase.BEFORE),
        HandlerPhase.BEFORE_MATCHED, new RouteKey(null, HandlerPhase.BEFORE_MATCHED),
        HandlerPhase.AFTER_MATCHED, new RouteKey(null, HandlerPhase.AFTER_MATCHED),
        HandlerPhase.AFTER, new RouteKey(null, HandlerPhase.AFTER)
    );

    static RouteKey http(HttpMethod method) {
        return HTTP_KEYS.computeIfAbsent(method, key -> new RouteKey(key, null));
    }

    static RouteKey phase(HandlerPhase phase) {
        return PHASE_KEYS.get(phase);
    }

}
