package io.javafast.internal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class StringMap {

    private String[] entries; // [key1][value2][key2][value2]...
    private int size;

    public StringMap(int initialCapacity) {
        this.entries = new String[initialCapacity << 1];
        this.size = 0;
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public void put(String key, String value) {
        final int index = findKey(key);
        if (index >= 0) {
            this.entries[index + 1] = value;
            return;
        }
        ensureCapacity(this.size + 1);
        final int offset = this.size << 1;
        this.entries[offset] = key;
        this.entries[offset + 1] = value;
        this.size++;
    }

    public String get(String key) {
        final int index = findKey(key);
        return index >= 0 ? this.entries[index + 1] : null;
    }

    public void remove(String key) {
        final int index = findKey(key);
        if (index >= 0) {
            this.entries[index + 1] = null;
            // remove doesn't change size, only makes the value null
        }
    }

    public boolean containsKey(String key) {
        return findKey(key) >= 0;
    }

    public String computeIfAbsent(String key, Function<String, String> mappingFunction) {
        final int index = findKey(key);
        if (index >= 0) {
            return this.entries[index + 1];
        }
        final String value = mappingFunction.apply(key);
        put(key, value);
        return value;
    }

    public String keyAt(int index) {
        checkIndex(index);
        return this.entries[index << 1];
    }

    public String valueAt(int index) {
        checkIndex(index);
        return this.entries[(index << 1) + 1];
    }

    public void clear() {
        this.size = 0;
    }

    public Map<String, String> toMap() {
        final Map<String, String> map = new HashMap<>(this.size);
        for (int i = 0; i < this.size; i++) {
            final int offset = i << 1;
            map.put(this.entries[offset], this.entries[offset + 1]);
        }
        return map;
    }

    private int findKey(String key) {
        for (int i = 0; i < this.size; i++) {
            final int offset = i << 1;
            final String entryKey = this.entries[offset];
            if (Objects.equals(entryKey, key) || entryKey != null && entryKey.equalsIgnoreCase(key)) {
                return offset;
            }
        }
        return -1;
    }

    private void ensureCapacity(int requiredSize) {
        final int requiredLength = requiredSize << 1;
        if (requiredLength <= this.entries.length) {
            return;
        }
        this.entries = Arrays.copyOf(this.entries, Math.max(requiredLength, this.entries.length << 1));
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= this.size) {
            throw new IndexOutOfBoundsException("index: " + index + ", size: " + this.size);
        }
    }
}
