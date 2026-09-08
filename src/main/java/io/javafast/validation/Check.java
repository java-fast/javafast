package io.javafast.validation;

@FunctionalInterface
public interface Check<T> {
    boolean test(T value);
}
