package io.javafast;

import io.javafast.runtime.ApplicationRuntime;

import java.util.function.Supplier;

public class JavaFastRuntime {

    private static final ScopedValue<ApplicationRuntime> STATE = ScopedValue.newInstance();

    static <T> T call(ApplicationRuntime runtime, Supplier<T> action) {
        return ScopedValue.where(STATE, runtime).call(action::get);
    }

    public static ApplicationRuntime current() {
        if (!STATE.isBound()) {
            throw new IllegalStateException("No JavaFast context is currently active");
        }
        return STATE.get();
    }

}
