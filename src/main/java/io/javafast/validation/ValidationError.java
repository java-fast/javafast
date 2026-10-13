package io.javafast.validation;

import java.util.Map;

public final class ValidationError<T> {

    private final String message;
    private final Map<String, Object> args;
    private Object value;
    private final Exception exception;

    public ValidationError(String message) {
        this(message, Map.of());
    }

    public ValidationError(String message, Map<String, Object> args) {
        this(message, args, null, null);
    }

    public ValidationError(String message, Map<String, Object> args, Object value, Exception exception) {
        this.message = message;
        this.args = args == null ? Map.of() : Map.copyOf(args);
        this.value = value;
        this.exception = exception;
    }

    public String message() {
        return this.message;
    }

    public Map<String, Object> args() {
        return this.args;
    }

    public T value() {
        return (T) this.value;
    }

    public Exception exception() {
        return this.exception;
    }

    void value(Object value) {
        this.value = value;
    }
}
