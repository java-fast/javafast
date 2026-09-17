package io.javafast.internal;

/**
 * Match result consumed by the core runtime
 */
final class RouteMatch {

    private Route route;
    private final PathParams pathParams = new PathParams();

    public void setRoute(Route route) {
        this.route = route;
    }

    public Route route() {
        return this.route;
    }

    public PathParams pathParams() {
        return this.pathParams;
    }

}
