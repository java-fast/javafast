package io.javafast.internal;

import io.javafast.http.HandlerPhase;
import io.javafast.http.HttpMethod;

record RouteKey(HttpMethod method, HandlerPhase phase) {

    private static final RouteKey[] HTTP_KEYS = new RouteKey[16];
    private static final RouteKey[] PHASE_KEYS = new RouteKey[4];

    static { // pre-populate known values
        HTTP_KEYS[HttpMethod.GET.index()] = new RouteKey(HttpMethod.GET, null);
        HTTP_KEYS[HttpMethod.POST.index()] = new RouteKey(HttpMethod.POST, null);
        HTTP_KEYS[HttpMethod.PUT.index()] = new RouteKey(HttpMethod.PUT, null);
        HTTP_KEYS[HttpMethod.DELETE.index()] = new RouteKey(HttpMethod.DELETE, null);
        HTTP_KEYS[HttpMethod.PATCH.index()] = new RouteKey(HttpMethod.PATCH, null);
        HTTP_KEYS[HttpMethod.HEAD.index()] = new RouteKey(HttpMethod.HEAD, null);
        HTTP_KEYS[HttpMethod.OPTIONS.index()] = new RouteKey(HttpMethod.OPTIONS, null);
        HTTP_KEYS[HttpMethod.TRACE.index()] = new RouteKey(HttpMethod.TRACE, null);
        HTTP_KEYS[HttpMethod.CONNECT.index()] = new RouteKey(HttpMethod.CONNECT, null);

        // TODO how to add new HttpMethods?

        PHASE_KEYS[HandlerPhase.BEFORE.ordinal()] = new RouteKey(null, HandlerPhase.BEFORE);
        PHASE_KEYS[HandlerPhase.BEFORE_MATCHED.ordinal()] = new RouteKey(null, HandlerPhase.BEFORE_MATCHED);
        PHASE_KEYS[HandlerPhase.AFTER_MATCHED.ordinal()] = new RouteKey(null, HandlerPhase.AFTER_MATCHED);
        PHASE_KEYS[HandlerPhase.AFTER.ordinal()] = new RouteKey(null, HandlerPhase.AFTER);
    }

    static RouteKey http(HttpMethod method) {
        return HTTP_KEYS[method.index()];
    }

    static RouteKey phase(HandlerPhase phase) {
        return PHASE_KEYS[phase.ordinal()];
    }

}
