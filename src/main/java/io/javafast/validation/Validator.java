package io.javafast.validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Fluent, request-local validator for a single value.
 */
public class Validator<T> {
    static final String TYPE_CONVERSION_FAILED = "TYPE_CONVERSION_FAILED";
    static final String NULLCHECK_FAILED = "NULLCHECK_FAILED";

    private final String fieldName;
    private final String stringValue;
    private final Supplier<?> valueSupplier;
    private final Object initialTypedValue;
    private final boolean hasInitialTypedValue;
    private final List<Rule> rules = new ArrayList<>();

    private boolean evaluated;
    private T typedValue;
    private Map<String, List<ValidationError<Object>>> errors;

    Validator(String fieldName, String stringValue, Supplier<?> valueSupplier) {
        this(fieldName, stringValue, valueSupplier, null, false);
    }

    Validator(String fieldName, Object typedValue) {
        this(fieldName, null, () -> null, typedValue, true);
    }

    private Validator(String fieldName, String stringValue, Supplier<?> valueSupplier,
                      Object typedValue, boolean hasInitialTypedValue) {
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName");
        this.stringValue = stringValue;
        this.valueSupplier = valueSupplier == null ? () -> null : valueSupplier;
        this.initialTypedValue = typedValue;
        this.hasInitialTypedValue = hasInitialTypedValue;
    }

    protected String conversionErrorMessage() {
        return TYPE_CONVERSION_FAILED;
    }

    public Validator<T> check(Check<T> check, String error) {
        return check(check, new ValidationError<>(error));
    }

    public Validator<T> check(Check<T> check, ValidationError<?> error) {
        addCheck(fieldName, check, error);
        return this;
    }

    @SuppressWarnings("unchecked")
    protected final void addCheck(String field, Check<?> check, ValidationError<?> error) {
        rules.add(new Rule(field, (Check<Object>) check, (ValidationError<Object>) error));
    }

    /**
     * Marks the value as required. get() already enforces this; this method is retained for API compatibility.
     */
    public Validator<T> required() {
        return this;
    }

    public boolean hasValue() {
        if (hasInitialTypedValue) {
            return initialTypedValue != null;
        }
        return stringValue != null && !stringValue.isEmpty();
    }

    public Map<String, List<ValidationError<T>>> errors() {
        evaluate();
        @SuppressWarnings("unchecked")
        Map<String, List<ValidationError<T>>> result = (Map<String, List<ValidationError<T>>>) (Map<?, ?>) errors;
        return result;
    }

    public T get() {
        evaluate();
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        if (typedValue == null) {
            throw new ValidationException(Map.of(fieldName,
                List.of(new ValidationError<>(NULLCHECK_FAILED, Map.of(), stringValue, null))));
        }
        return typedValue;
    }

    public T getOrNull() {
        evaluate();
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return typedValue;
    }

    public T getOrDefault(T defaultValue) {
        T value = getOrNull();
        return value == null ? defaultValue : value;
    }

    public T getOrThrow(java.util.function.Function<Map<String, List<ValidationError<Object>>>, ? extends Exception> exceptionFunction) {
        evaluate();
        if (!errors.isEmpty()) {
            throwUnchecked(exceptionFunction.apply(errors));
        }
        if (typedValue == null) {
            throwUnchecked(exceptionFunction.apply(Map.of(fieldName,
                List.of(new ValidationError<>(NULLCHECK_FAILED, Map.of(), stringValue, null)))));
        }
        return typedValue;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception> void throwUnchecked(E exception) {
        Validator.<RuntimeException>throwAny(exception);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwAny(Throwable throwable) throws E {
        throw (E) throwable;
    }

    private void evaluate() {
        if (evaluated) {
            return;
        }
        evaluated = true;

        try {
            @SuppressWarnings("unchecked")
            T value = (T) (hasInitialTypedValue ? initialTypedValue : valueSupplier.get());
            typedValue = value;
        } catch (Exception e) {
            errors = Map.of(fieldName, List.of(
                new ValidationError<>(conversionErrorMessage(), Map.of(), stringValue, e)));
            return;
        }

        Map<String, List<ValidationError<Object>>> failed = new LinkedHashMap<>();
        for (Rule rule : rules) {
            if (!rule.check().test(typedValue)) {
                rule.error().value(typedValue);
                failed.computeIfAbsent(rule.fieldName(), ignored -> new ArrayList<>()).add(rule.error());
            }
        }
        failed.replaceAll((key, value) -> List.copyOf(value));
        errors = Map.copyOf(failed);
    }

    record Rule(String fieldName, Check<Object> check, ValidationError<Object> error) {
    }
}
