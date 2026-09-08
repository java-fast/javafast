package io.javafast.config;

import java.util.HashMap;
import java.util.Map;

public class AppDataManager {

    private final Map<String, Object> data = new HashMap<>();

    public <T> void register(String key, T value) {
        if (data.containsKey(key)) {
            throw new KeyAlreadyExistsException(key);
        }
        data.put(key, value);
    }

    public <T> void registerIfAbsent(String key, T value) {
        data.putIfAbsent(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        Object value = data.get(key);

        if (value == null) {
            throw new NoValueForKeyException(key);
        }

        return (T) value;
    }

}
