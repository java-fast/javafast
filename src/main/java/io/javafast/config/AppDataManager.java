package io.javafast.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppDataManager implements AppData {

    private final Map<String, Object> data = new ConcurrentHashMap<>();

    @Override
    public <T> void register(String key, T value) {
        if (this.data.containsKey(key)) {
            throw new KeyAlreadyExistsException(key);
        }
        this.data.put(key, value);
    }

    @Override
    public <T> void registerIfAbsent(String key, T value) {
        this.data.putIfAbsent(key, value);
    }

    @Override
    public <T> T get(String key) {
        final Object value = this.data.get(key);
        if (value == null) {
            throw new NoValueForKeyException(key);
        }
        return (T) value;
    }

}
