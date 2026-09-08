package io.javafast.plugin;

import java.util.function.Consumer;

public abstract class Plugin<CONFIG> {

    public Plugin(Consumer<CONFIG> userConfig, CONFIG defaultConfig) {}

    public boolean repeatable() {
        return false;
    }

    public PluginPriority priority() {
        return PluginPriority.NORMAL;
    }

    public String name() {
        return "";
    }

}
