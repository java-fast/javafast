package io.javafast.internal;

import io.javafast.http.HandlerPhase;
import io.javafast.http.HttpMethod;

record RouteKey(HttpMethod method, HandlerPhase phase) {

    static RouteKey http(HttpMethod method) {
        return new RouteKey(method, null);
    }

    static RouteKey phase(HandlerPhase phase) {
        return new RouteKey(null, phase);
    }

}
