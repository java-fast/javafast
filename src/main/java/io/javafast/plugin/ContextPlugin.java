package io.javafast.plugin;

import io.javafast.http.Context;

import java.util.function.Consumer;

public abstract class ContextPlugin<CONFIG, EXTENSION> extends Plugin<CONFIG> {

    public ContextPlugin(Consumer<CONFIG> userConfig, CONFIG defaultConfig) {
        super(userConfig, defaultConfig);
    }

    public EXTENSION createExtension(Context context) {
        return null;
    }

}
