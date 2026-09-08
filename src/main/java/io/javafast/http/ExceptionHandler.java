package io.javafast.http;

public interface ExceptionHandler<T extends Exception> {
    void handle(T exception, Context context);
}
