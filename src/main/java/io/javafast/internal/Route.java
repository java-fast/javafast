package io.javafast.internal;

import io.javafast.http.Handler;
import io.javafast.http.HandlerPhase;
import io.javafast.http.HttpMethod;
import io.javafast.security.AccessController;

/**
 * Internal route definition.
 */
record Route(HttpMethod method, HandlerPhase phase, String path, Handler handler, AccessController accessController, RoutePattern pattern) {
}
