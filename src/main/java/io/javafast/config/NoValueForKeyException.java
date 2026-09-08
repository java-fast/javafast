package io.javafast.config;

public class NoValueForKeyException extends IllegalStateException {

    public NoValueForKeyException(String key) {
        super("No value for key '" + key + "'");
    }

}
