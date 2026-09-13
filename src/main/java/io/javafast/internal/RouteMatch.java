package io.javafast.internal;

import java.util.Map;

/**
 * Match result consumed by the core runtime
 */
record RouteMatch(Route route, Map<String, String> pathParams) {

}
