package io.javafast.runtime;

import io.javafast.JavaFastRuntime;
import io.javafast.config.AppData;
import io.javafast.internal.DefaultRouting;

/**
 * Runtime that is allowed to expose in {@link JavaFastRuntime}
 */
public interface ApplicationRuntime {

    DefaultRouting routing();

    AppData appData();

    // possibly we expose more config to the runtime in the feature

}
