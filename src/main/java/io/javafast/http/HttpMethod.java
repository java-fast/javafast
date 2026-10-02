package io.javafast.http;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP method name. Supports standard and custom methods.
 */
public record HttpMethod(int key, String name) {

    static final int MAX_COUNT = Integer.getInteger("javafast.http.methods.max.count", 16);

    private static final AtomicInteger incrementer = new AtomicInteger();
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
        if (key > MAX_COUNT) throw new IllegalArgumentException("HTTP method index must not be greater than " + MAX_COUNT);
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("HTTP method must not be empty");
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (c < 'A' || c > 'Z') throw new IllegalArgumentException("Invalid HTTP method: " + name);
        }
    }

    public static HttpMethod of(String name) {
        return CACHE.computeIfAbsent(name.toUpperCase(Locale.ROOT), s -> new HttpMethod(incrementer.getAndIncrement(), s));
    }

    @Override
    public String toString() {
        return this.name;
    }
}
