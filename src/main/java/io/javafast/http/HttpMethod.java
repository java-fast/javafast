package io.javafast.http;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP method name. Supports standard and custom methods.
 */
public record HttpMethod(String name) {

    private static final Map<String, HttpMethod> CACHE = new ConcurrentHashMap<>();

    public static final HttpMethod GET = of("GET");
    public static final HttpMethod POST = of("POST");
    public static final HttpMethod PUT = of("PUT");
    public static final HttpMethod PATCH = of("PATCH");
    public static final HttpMethod DELETE = of("DELETE");
    public static final HttpMethod HEAD = of("HEAD");
    public static final HttpMethod OPTIONS = of("OPTIONS");
    public static final HttpMethod TRACE = of("TRACE");
    public static final HttpMethod CONNECT = of("CONNECT");

    public HttpMethod {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("HTTP method must not be empty");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 'A' || c > 'Z') throw new IllegalArgumentException("Invalid HTTP method: " + name);
        }
    }

    public static HttpMethod of(String name) {
        return CACHE.computeIfAbsent(name.toUpperCase(Locale.ROOT), HttpMethod::new);
    }

    @Override
    public String toString() {
        return name;
    }
}
