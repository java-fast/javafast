package io.javafast.internal;

import io.javafast.http.HandlerPhase;
import io.javafast.http.HttpMethod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

record RouteKey(HttpMethod method, HandlerPhase phase) {

    private static final Map<HttpMethod, RouteKey> HTTP_KEYS = new ConcurrentHashMap<>();
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
