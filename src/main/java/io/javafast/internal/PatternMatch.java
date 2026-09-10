package io.javafast.internal;

import java.util.Collections;
import java.util.Map;

record PatternMatch(Map<String, String> pathParams) {
    static final PatternMatch EMPTY = new PatternMatch(Collections.emptyMap());
}
