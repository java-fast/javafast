package io.javafast.internal;

/**
 * Match result consumed by the core runtime
 */
final class RouteMatch {

    private Route route;

    final PatternMatch patternMatch = new PatternMatch();

    public void setRoute(Route route) {
        this.route = route;
    }

    public Route route() {
        return this.route;
    }

}
