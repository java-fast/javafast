package io.javafast.config;

public interface AppData {

    <T> void register(String key, T value);

    <T> void registerIfAbsent(String key, T value);

    <T> T get(String key);

}
