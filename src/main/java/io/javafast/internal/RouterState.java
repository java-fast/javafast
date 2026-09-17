package io.javafast.internal;

/**
 * Thread-confined state for a single Router
 */
final class RouterState {

    final RouteMatch routeMatch = new RouteMatch();
    final RouteMatch phaseMatch = new RouteMatch();

}
