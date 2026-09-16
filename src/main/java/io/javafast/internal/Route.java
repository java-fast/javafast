package io.javafast.internal;

import io.javafast.http.Handler;
import io.javafast.http.HandlerPhase;
import io.javafast.http.HttpMethod;

/**
 * Internal route definition.
 */
record Route(HttpMethod method, HandlerPhase phase, String path, Handler handler, RoutePattern pattern) {
}
