package io.javafast.util.function;

public interface ThrowingSupplier<T> {
    T get() throws Exception;
}
