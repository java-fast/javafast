package io.javafast.internal.router;

import java.util.Map;

/**
 * Match result consumed by the core runtime. This package is not exported by the JavaFast module.
 */
record RouteMatch(Route route, Map<String, String> pathParams) {
    public RouteMatch {
        pathParams = Map.copyOf(pathParams);
    }
}
