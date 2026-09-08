package io.javafast.validation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Factory and converter registry behind JavaFast's validation API. */
public final class Validation {
    private final Map<Class<?>, Function<String, ?>> converters = new HashMap<>();

    public Validation() {
        registerDefaults();
    }

    public <T> Validation register(Class<T> clazz, Function<String, ? extends T> converter) {
        converters.put(clazz, converter);
        return this;
    }

    public <T> Validator<T> validator(String fieldName, Class<T> clazz, String value) {
        if (!supportsClass(clazz)) {
            throw new MissingConverterException(clazz.getName());
        }
        return new Validator<>(fieldName, value, () -> convertValue(clazz, value));
    }

    public <T> Validator<T> validator(String fieldName, T typedValue) {
        return new Validator<>(fieldName, typedValue);
    }

    public static Map<String, List<ValidationError<?>>> collectErrors(Validator<?>... validators) {
        List<Validator<?>> list = List.of(validators);
        return collectErrors(list);
    }

    public static Map<String, List<ValidationError<?>>> collectErrors(Iterable<? extends Validator<?>> validators) {
        Map<String, List<ValidationError<?>>> result = new HashMap<>();
        for (Validator<?> validator : validators) {
            validator.errors().forEach((field, errors) ->
                    result.computeIfAbsent(field, ignored -> new ArrayList<>()).addAll(errors));
        }
        result.replaceAll((key, value) -> List.copyOf(value));
        return Map.copyOf(result);
    }

    private boolean supportsClass(Class<?> clazz) {
        return converters.containsKey(clazz) || clazz.isEnum();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T convertValue(Class<T> clazz, String value) {
        if (clazz.isEnum()) {
            return (T) Enum.valueOf((Class<? extends Enum>) clazz, value);
        }
        Function<String, ?> converter = converters.get(clazz);
        if (converter == null) {
            throw new MissingConverterException(clazz.getName());
        }
        return (T) converter.apply(value);
    }

    private void registerDefaults() {
        register(String.class, value -> value);
        register(Integer.class, Integer::valueOf);
        register(int.class, Integer::valueOf);
        register(Long.class, Long::valueOf);
        register(long.class, Long::valueOf);
        register(Double.class, Double::valueOf);
        register(double.class, Double::valueOf);
        register(Float.class, Float::valueOf);
        register(float.class, Float::valueOf);
        register(Boolean.class, Boolean::parseBoolean);
        register(boolean.class, Boolean::parseBoolean);
        register(Short.class, Short::valueOf);
        register(short.class, Short::valueOf);
        register(Byte.class, Byte::valueOf);
        register(byte.class, Byte::valueOf);
    }

    public static final class MissingConverterException extends RuntimeException {
        public MissingConverterException(String className) {
            super("No converter registered for class: " + className);
        }
    }
}
