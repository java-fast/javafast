package io.javafast.validation;

import java.util.List;
import java.util.Map;

public class ValidationException extends RuntimeException {
    private final Map<String, List<ValidationError<Object>>> errors;

    public ValidationException(Map<String, List<ValidationError<Object>>> errors) {
        super("Validation failed");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, List<ValidationError<Object>>> errors() {
        return errors;
    }
}
