package io.javafast.validation

import spock.lang.Specification

class ValidationSpec extends Specification {

    private enum Role {
        ADMIN, USER
    }

    private static final class UserId {
        final int value

        UserId(int value) {
            this.value = value
        }

        @Override
        boolean equals(Object o) {
            if (this.is(o)) return true
            if (!(o instanceof UserId)) return false
            return value == o.value
        }

        @Override
        int hashCode() {
            return Integer.hashCode(value)
        }
    }

    private static final class User {
        final String name

        User(String name) {
            this.name = name
        }
    }

    def "should convert built-in types"() {
        given:
        def validation = new Validation()

        expect:
        validation.validator("age", Integer, "42").get() == 42
        validation.validator("id", Long, "42").get() == 42L
        validation.validator("ratio", Double, "3.14").get() == 3.14d
        validation.validator("enabled", Boolean, "true").get()
        validation.validator("role", Role, "ADMIN").get() == Role.ADMIN
    }

    def "should allow registering custom converter"() {
        given:
        def validation = new Validation().register(UserId, { value -> new UserId(Integer.parseInt(value)) })

        expect:
        validation.validator("userId", UserId, "123").get() == new UserId(123)
    }

    def "should throw validation exception when check fails"() {
        given:
        def validation = new Validation()
        def validator = validation.validator("age", Integer, "17")
            .check({ value -> value >= 18 }, "Must be at least 18")

        when:
        validator.get()

        then:
        def exception = thrown(ValidationException)
        exception.errors().get("age").first().message() == "Must be at least 18"
    }

    def "should collect multiple check failures across fields"() {
        given:
        def validation = new Validation()
        def first = validation.validator("name", String, "x")
            .check({ value -> value.length() >= 3 }, "Too short")
            .check({ value -> value ==~ /[A-Z].*/ }, "Must start with uppercase")
        def second = validation.validator("age", Integer, "10")
            .check({ value -> value >= 18 }, "Too young")

        when:
        def errors = Validation.collectErrors(first, second)

        then:
        errors.get("name").size() == 2
        errors.get("name").first().message() == "Too short"
        errors.get("name").last().message() == "Must start with uppercase"
        errors.get("age").first().message() == "Too young"
    }

    def "should merge duplicate field errors"() {
        given:
        def validation = new Validation()
        def first = validation.validator("name", String, "x").check({ false }, "first")
        def second = validation.validator("name", String, "x").check({ false }, "second")

        when:
        def errors = Validation.collectErrors([first, second])

        then:
        errors.get("name")*.message() == ["first", "second"]
    }

    def "should report conversion failure"() {
        given:
        def validation = new Validation()

        when:
        validation.validator("age", Integer, "not-a-number").get()

        then:
        def exception = thrown(ValidationException)
        def error = exception.errors().get("age").first()
        error.message() == "TYPE_CONVERSION_FAILED"
        error.exception() != null
    }

    def "should fail validator creation when converter is missing"() {
        given:
        def validation = new Validation()

        when:
        validation.validator("id", UserId, "123")

        then:
        thrown(Validation.MissingConverterException)
    }

    def "should use deserialization failure for body validator"() {
        given:
        def validator = new BodyValidator<Object>("{}", {
            throw new IllegalArgumentException("bad json")
        })

        when:
        validator.get()

        then:
        def exception = thrown(ValidationException)
        def error = exception.errors().get(BodyValidator.REQUEST_BODY).first()
        error.message() == "DESERIALIZATION_FAILED"
        error.exception() != null
    }

    def "should validate named fields on body validator"() {
        given:
        def validator = new BodyValidator<User>("body", { new User("bob") })
            .check("username", { user -> user.name.length() >= 3 }, "Username too short")

        expect:
        validator.get().name == "bob"
    }

    def "should return default for typed null value"() {
        given:
        def validation = new Validation()
        def validator = validation.validator("name", (String) null)

        expect:
        validator.getOrDefault("fallback") == "fallback"
        validator.getOrNull() == null
    }

    def "should use caller exception factory in getOrThrow"() {
        given:
        def validation = new Validation()
        def validator = validation.validator("age", Integer, "17")
            .check({ value -> value >= 18 }, "Too young")

        when:
        validator.getOrThrow({ errors -> new IllegalStateException(errors.keySet().toString()) })

        then:
        def exception = thrown(IllegalStateException)
        exception.message == "[age]"
    }
}
