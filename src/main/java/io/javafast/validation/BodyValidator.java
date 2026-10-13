package io.javafast.validation;

import java.util.function.Predicate;
import java.util.function.Supplier;

public final class BodyValidator<T> extends Validator<T> {

    public static final String REQUEST_BODY = "REQUEST_BODY";

    BodyValidator(String body, Supplier<?> valueSupplier) {
        super(REQUEST_BODY, body, valueSupplier);
    }

    @Override
    protected String conversionErrorMessage() {
        return "DESERIALIZATION_FAILED";
    }

    public BodyValidator<T> check(String fieldName, Predicate<T> check, String error) {
        return check(fieldName, check, new ValidationError<>(error));
    }

    public BodyValidator<T> check(String fieldName, Predicate<T> check, ValidationError<?> error) {
        addCheck(fieldName, check, error);
        return this;
    }
}
