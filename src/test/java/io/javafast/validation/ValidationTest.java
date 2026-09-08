package io.javafast.validation;

import java.util.List;
import java.util.Map;

/**
 * Lightweight test suite with no test-framework dependency. Run with assertions enabled.
 */
public final class ValidationTest {
    enum Role {ADMIN, USER}

    public static void main(String[] args) {
        convertsBuiltInTypes();
        customConverterCanBeRegistered();
        checkFailureProducesValidationException();
        multipleChecksAndFieldsAreCollected();
        duplicateFieldErrorsAreMerged();
        conversionFailureIsReported();
        missingConverterFailsAtValidatorCreation();
        bodyValidatorUsesDeserializationError();
        bodyValidatorCanValidateNamedFields();
        getOrDefaultReturnsDefaultForTypedNull();
        getOrThrowUsesCallerExceptionFactory();
        System.out.println("ValidationTest: all tests passed");
    }

    static void convertsBuiltInTypes() {
        Validation v = new Validation();
        assertEquals(42, v.validator("age", Integer.class, "42").get());
        assertEquals(42L, v.validator("id", Long.class, "42").get());
        assertEquals(3.14, v.validator("ratio", Double.class, "3.14").get());
        assertTrue(v.validator("enabled", Boolean.class, "true").get());
        assertEquals(Role.ADMIN, v.validator("role", Role.class, "ADMIN").get());
    }

    static void customConverterCanBeRegistered() {
        Validation v = new Validation().register(UserId.class, s -> new UserId(Integer.parseInt(s)));
        assertEquals(new UserId(123), v.validator("userId", UserId.class, "123").get());
    }

    static void checkFailureProducesValidationException() {
        Validation v = new Validation();
        Validator<Integer> validator = v.validator("age", Integer.class, "17")
            .check(value -> value >= 18, "Must be at least 18");
        ValidationException e = expect(ValidationException.class, validator::get);
        assertEquals("Must be at least 18", e.errors().get("age").get(0).message());
    }

    static void multipleChecksAndFieldsAreCollected() {
        Validation v = new Validation();
        Validator<String> first = v.validator("name", String.class, "x")
            .check(value -> value.length() >= 3, "Too short")
            .check(value -> value.matches("[A-Z].*"), "Must start with uppercase");
        Validator<Integer> second = v.validator("age", Integer.class, "10")
            .check(value -> value >= 18, "Too young");
        Map<String, List<ValidationError<?>>> errors = Validation.collectErrors(first, second);
        assertEquals(2, errors.get("name").size());
        assertEquals("Too young", errors.get("age").get(0).message());
    }

    static void duplicateFieldErrorsAreMerged() {
        Validation v = new Validation();
        Validator<String> first = v.validator("name", String.class, "x").check(value -> false, "first");
        Validator<String> second = v.validator("name", String.class, "x").check(value -> false, "second");
        Map<String, List<ValidationError<?>>> errors = Validation.collectErrors(List.of(first, second));
        assertEquals(List.of("first", "second"), errors.get("name").stream().map(ValidationError::message).toList());
    }

    static void conversionFailureIsReported() {
        Validation v = new Validation();
        ValidationException e = expect(ValidationException.class,
            () -> v.validator("age", Integer.class, "not-a-number").get());
        ValidationError<?> error = e.errors().get("age").get(0);
        assertEquals("TYPE_CONVERSION_FAILED", error.message());
        assertTrue(error.exception() != null);
    }

    static void missingConverterFailsAtValidatorCreation() {
        Validation v = new Validation();
        expect(Validation.MissingConverterException.class,
            () -> v.validator("id", UserId.class, "123"));
    }

    static void bodyValidatorUsesDeserializationError() {
        BodyValidator<Object> validator = new BodyValidator<>("{}", Object.class,
            () -> {
                throw new IllegalArgumentException("bad json");
            });
        ValidationException e = expect(ValidationException.class, validator::get);
        ValidationError<?> error = e.errors().get(BodyValidator.REQUEST_BODY).get(0);
        assertEquals("DESERIALIZATION_FAILED", error.message());
        assertTrue(error.exception() != null);
    }

    static void bodyValidatorCanValidateNamedFields() {
        BodyValidator<User> validator = new BodyValidator<User>("body", User.class, () -> new User("bob"))
            .check("username", user -> user.name().length() >= 3, "Username too short");
        assertEquals("bob", validator.get().name());
    }

    static void getOrDefaultReturnsDefaultForTypedNull() {
        Validation v = new Validation();
        Validator<String> validator = v.validator("name", (String) null);
        assertEquals("fallback", validator.getOrDefault("fallback"));
        assertTrue(validator.getOrNull() == null);
    }

    static void getOrThrowUsesCallerExceptionFactory() {
        Validation v = new Validation();
        Validator<Integer> validator = v.validator("age", Integer.class, "17")
            .check(value -> value >= 18, "Too young");
        IllegalStateException e = expect(IllegalStateException.class,
            () -> validator.getOrThrow(errors -> new IllegalStateException(errors.keySet().toString())));
        assertEquals("[age]", e.getMessage());
    }

    private static void assertTrue(boolean condition) {
        if (!condition) throw new AssertionError("expected condition to be true");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static <T extends Throwable> T expect(Class<T> type, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return type.cast(t);
            throw new AssertionError("expected " + type.getName() + " but got " + t, t);
        }
        throw new AssertionError("expected " + type.getName() + " to be thrown");
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Throwable;
    }

    record UserId(int value) {
    }

    record User(String name) {
    }
}
