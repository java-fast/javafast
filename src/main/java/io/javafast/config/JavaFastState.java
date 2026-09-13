package io.javafast.config;

import io.javafast.json.JsonMapper;
import io.javafast.plugin.Plugin;
import io.javafast.transport.Transport;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class JavaFastState {

    public JavaFastState() {}

    // CORE CONFIGS - HTTP, routing, and transport
    public final HttpConfig http = new HttpConfig();
    public final RouterConfig router = new RouterConfig();
    public Supplier<Transport> transport;

    // FEATURE CONFIGS - routes
    public final RoutesConfig routes = new RoutesConfig(this.router);

    // CROSS-CUTTING CONFIGS - Validation, context resolution, plugins
    public final ValidationConfig validation = new ValidationConfig();
    public final ContextResolverConfig contextResolver = new ContextResolverConfig();

    // MISC SETTINGS - General application-level settings
    public final StartupConfig startup = new StartupConfig();
    public final ConcurrencyConfig concurrency = new ConcurrencyConfig();

    public JsonMapper jsonMapper = null; // lazy?
    public Object appDataManager;
    public Object pluginManager;
//    public List<RequestLogger> httpRequestLoggers;

    public <CFG> Plugin<CFG> registerPlugin(Plugin<CFG> plugin) {
        // TODO implement
        return plugin;
    }

    public <T> void appData(String key, T value) {}

    public static void applyUserConfig(JavaFastState cfg, Consumer<JavaFastConfig> userConfig) {}
}
