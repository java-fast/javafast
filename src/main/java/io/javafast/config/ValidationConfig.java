package io.javafast.config;

import java.util.function.Function;

public class ValidationConfig {
    public ValidationConfig() {}
    public void register(Class<?> clazz, Function<String, Object> converter) {}
}
