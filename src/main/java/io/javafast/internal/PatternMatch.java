package io.javafast.internal;

import java.util.Collections;
import java.util.Map;

/**
 * TODO maybe we don't need this class, only Map<String, String> pathParams?
 * Match result consumed by the core runtime
 * @param pathParams
 */
record PatternMatch(Map<String, String> pathParams) {

    static final PatternMatch EMPTY = new PatternMatch(Collections.emptyMap());

}
