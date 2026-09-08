package io.javafast.util.function;

public interface ThrowingConsumer<T> {
    void accept(T t) throws Exception;
}
