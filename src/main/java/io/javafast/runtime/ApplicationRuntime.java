package io.javafast.runtime;

import io.javafast.JavaFastRuntime;
import io.javafast.internal.DefaultRouting;

/**
 * Runtime that is allowed to expose in {@link JavaFastRuntime}
 */
public interface ApplicationRuntime {

    DefaultRouting routing();

    // maybe we expose more config to the runtime in the feature

}
