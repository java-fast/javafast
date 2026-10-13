package io.javafast.validation;

import java.util.*;
import java.util.function.Predicate;
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

    private Validator(String fieldName, String stringValue, Supplier<?> valueSupplier, Object typedValue, boolean hasInitialTypedValue) {
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName");
        this.stringValue = stringValue;
        this.valueSupplier = valueSupplier == null ? () -> null : valueSupplier;
        this.initialTypedValue = typedValue;
        this.hasInitialTypedValue = hasInitialTypedValue;
    }

    protected String conversionErrorMessage() {
        return TYPE_CONVERSION_FAILED;
    }

    public Validator<T> check(Predicate<T> check, String error) {
        return check(check, new ValidationError<>(error));
    }

    public Validator<T> check(Predicate<T> check, ValidationError<?> error) {
        addCheck(this.fieldName, check, error);
        return this;
    }

    protected final void addCheck(String field, Predicate<?> check, ValidationError<?> error) {
        this.rules.add(new Rule(field, (Predicate<Object>) check, (ValidationError<Object>) error));
    }

    /**
     * Marks the value as required. get() already enforces this; this method is retained for API compatibility.
     */
    public Validator<T> required() {
        return this;
    }

    public boolean hasValue() {
        if (this.hasInitialTypedValue) {
            return this.initialTypedValue != null;
        }
        return this.stringValue != null && !this.stringValue.isEmpty();
    }

    public Map<String, List<ValidationError<T>>> errors() {
        evaluate();
        return (Map<String, List<ValidationError<T>>>) (Map<?, ?>) this.errors;
    }

    public T get() {
        evaluate();
        if (!this.errors.isEmpty()) {
            throw new ValidationException(this.errors);
        }
        if (this.typedValue == null) {
            throw new ValidationException(Map.of(this.fieldName, List.of(new ValidationError<>(NULLCHECK_FAILED, Map.of(), this.stringValue, null))));
        }
        return this.typedValue;
    }

    public T getOrNull() {
        evaluate();
        if (!this.errors.isEmpty()) {
            throw new ValidationException(this.errors);
        }
        return this.typedValue;
    }

    public T getOrDefault(T defaultValue) {
        final T value = getOrNull();
        return value == null ? defaultValue : value;
    }

    public T getOrThrow(java.util.function.Function<Map<String, List<ValidationError<Object>>>, ? extends Exception> exceptionFunction) {
        evaluate();
        if (!this.errors.isEmpty()) {
            throwUnchecked(exceptionFunction.apply(this.errors));
        }
        if (this.typedValue == null) {
            throwUnchecked(exceptionFunction.apply(Map.of(this.fieldName, List.of(new ValidationError<>(NULLCHECK_FAILED, Map.of(), this.stringValue, null)))));
        }
        return this.typedValue;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception> void throwUnchecked(E exception) {
        Validator.throwAny(exception);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwAny(Throwable throwable) throws E {
        throw (E) throwable;
    }

    private void evaluate() {
        if (this.evaluated) {
            return;
        }
        this.evaluated = true;

        try {
            @SuppressWarnings("unchecked")
            final T value = (T) (this.hasInitialTypedValue ? this.initialTypedValue : this.valueSupplier.get());
            this.typedValue = value;
        } catch (Exception e) {
            this.errors = Map.of(this.fieldName, List.of(new ValidationError<>(conversionErrorMessage(), Map.of(), this.stringValue, e)));
            return;
        }

        final var failed = new LinkedHashMap<String, List<ValidationError<Object>>>();
        for (Rule rule : this.rules) {
            if (!rule.check().test(this.typedValue)) {
                rule.error().value(this.typedValue);
                failed.computeIfAbsent(rule.fieldName(), ignored -> new ArrayList<>()).add(rule.error());
            }
        }
        failed.replaceAll((key, value) -> List.copyOf(value));
        this.errors = Map.copyOf(failed);
    }

    record Rule(String fieldName, Predicate<Object> check, ValidationError<Object> error) {
    }

}
