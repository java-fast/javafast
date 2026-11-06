package io.javafast.internal;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Match of path params consumed by the core runtime
 */
final class PathParams {

    private final PathParam[] params;
    private ByteString requestPath;
    private int size;

    public PathParams(int maxSize) {
        this.params = new PathParam[maxSize];
        for (int i = 0; i < maxSize; i++) {
            this.params[i] = new PathParam();
        }
    }

    void reset(ByteString requestPath) {
        this.requestPath = requestPath;
        this.size = 0;
    }

    void add(String name, int start, int length) {
        this.params[this.size++].set(name, start, length);
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public int size() {
        return this.size;
    }

    String get(int index) {
        if (index >= this.size || index < 0) return null;
        return this.params[index].get();
    }

    /**
     * Prefer get(int index) if possible, which is faster
     */
    public String get(String name) {
        for (int i = 0; i < this.size; i++) {
            final PathParam param = this.params[i];
            if (name.equals(param.name)) {
                return param.get();
            }
        }
        return null;
    }

    Map<String, String> get() {
        final Map<String, String> map = new HashMap<>(this.size);
        for (int i = 0; i < this.size; i++) {
            final PathParam param = this.params[i];
            map.put(param.name, param.get());
        }
        return map;
    }

    /**
     * Thread-confiden path param, lazy value extraction
     */
    private class PathParam {

        private String name;
        private int start;
        private int length;

        private String value; // lazy

        private void set(String name, int start, int length) {
            this.value = null; // clear value cache
            this.name = name;
            this.start = start;
            this.length = length;
        }

        private String get() {
            if (this.value == null) {
                this.value = decode(requestPath.substring(this.start, this.start + this.length));
            }
            return this.value;
        }

        private static String decode(String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }

    }

}
