package io.javafast.internal;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Match of path params consumed by the core runtime
 */
final class PathParams extends HashMap<String, String> {

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
        clear();
    }

    void add(String name, int start, int length) {
        this.params[this.size++].set(name, start, length);
    }

    @Override
    public boolean isEmpty() {
        return this.size == 0;
    }

    @Override
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
    String get(String name) {
        return computeIfAbsent(name, key -> {
            for (int i = 0; i < this.size; i++) {
                final PathParam param = this.params[i];
                if (key.equals(param.name)) {
                    return param.get();
                }
            }
            return null;
        });
    }

    Map<String, String> get() {
        for (int i = 0; i < this.size; i++) {
            final PathParam param = this.params[i];
            put(param.name, param.get());
        }
        return this;
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
