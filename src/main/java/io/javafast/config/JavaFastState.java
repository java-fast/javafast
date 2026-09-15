package io.javafast.config;

import io.javafast.internal.DefaultRouting;
import io.javafast.json.JsonMapper;
import io.javafast.plugin.Plugin;
import io.javafast.runtime.ApplicationRuntime;

import java.util.function.Consumer;

public class JavaFastState implements ApplicationRuntime {

    // APP DATA - Per application key value store
    final AppDataManager appDataManager = new AppDataManager();

    // CORE CONFIGS - HTTP, routing, and transport
    public final HttpConfig http = new HttpConfig();
    public final RouterConfig router = new RouterConfig();
    public final TransportFactoryConfig transport = new TransportFactoryConfig();

    // FEATURE CONFIGS - routes
    public final RoutesConfig routes = new RoutesConfig(this.router);

    // CROSS-CUTTING CONFIGS - Validation, context resolution, plugins
    public final ValidationConfig validation = new ValidationConfig();
    public final ContextResolverConfig contextResolver = new ContextResolverConfig();

    // MISC SETTINGS - General application-level settings
    public final StartupConfig startup = new StartupConfig();
    public final ConcurrencyConfig concurrency = new ConcurrencyConfig();

    public JsonMapper jsonMapper = null; // lazy?
    public Object pluginManager;
//    public List<RequestLogger> httpRequestLoggers;

    public <CFG> Plugin<CFG> registerPlugin(Plugin<CFG> plugin) {
        // TODO implement
        return plugin;
    }

    @Override
    public DefaultRouting routing() {
        return this.routes.routing;
    }

    @Override
    public AppData appData() {
        return this.appDataManager;
    }
}
