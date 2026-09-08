package io.javafast.config;

public class KeyAlreadyExistsException extends IllegalStateException {

    public KeyAlreadyExistsException(String key) {
        super("Key '" + key + "' already exists");
    }

}
