package io.javafast;

import io.javafast.internal.DefaultRouting;
import io.javafast.runtime.ApplicationRuntime;

import java.util.function.Supplier;

public record JavaFastRuntime(DefaultRouting routing) implements ApplicationRuntime {

    private static final ScopedValue<ApplicationRuntime> STATE = ScopedValue.newInstance();

    public static ApplicationRuntime current() {
        if (!STATE.isBound()) {
            throw new IllegalStateException("No JavaFast context is currently active");
        }
        return STATE.get();
    }

    static <T> T call(ApplicationRuntime runtime, Supplier<T> action) {
        return ScopedValue.where(STATE, runtime).call(action::get);
    }

}
