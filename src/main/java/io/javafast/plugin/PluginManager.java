package io.javafast.plugin;

public class PluginManager {

    public void register(Plugin<?> plugin) {
    }

    public void startPlugins() {
    }

    public <T> ContextPlugin<?, T> getContextPlugin(Class<? extends ContextPlugin<?, T>> clazz) {
        return null;
    }
}
