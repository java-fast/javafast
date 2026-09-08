package io.javafast.config;

import io.javafast.event.EventManager;
import io.javafast.json.JsonMapper;
import io.javafast.plugin.Plugin;

import java.util.function.Consumer;

public class JavaFastState {

    public JavaFastState() {}

    // CORE CONFIGS - HTTP, routing, and server
    public final HttpConfig http = new HttpConfig();
    public final RouterConfig router = new RouterConfig();

    // FEATURE CONFIGS - StaticFileConfig files, SPAs, and routes
//    public final RoutesConfig routes = new RoutesConfig(this);

    // CROSS-CUTTING CONFIGS - Validation, context resolution, logging, plugins, events
    public final ValidationConfig validation = new ValidationConfig();
    public final ContextResolverConfig contextResolver = new ContextResolverConfig();
    public final EventConfig events = new EventConfig(this);

    // MISC SETTINGS - General application-level settings
    public final StartupConfig startup = new StartupConfig();
    public final ConcurrencyConfig concurrency = new ConcurrencyConfig();

    // INTERNAL CONFIG API
    public final EventManager eventManager = new EventManager();

//    public InternalRouter internalRouter = new InternalRouter(null);

    public JsonMapper jsonMapper = null; // lazy?

    public Object appDataManager;
    public Object pluginManager;
//    public List<RequestLogger> httpRequestLoggers;
    public Object singlePageHandler;

//    public Servlet servlet; // lazy?

    public Object jettyInternal;
    public Object servletRequestLifecycle;

//    @SafeVarargs
//    public final void requestLifeCycle(TaskInitializer<JavalinServletContext>... lifecycle) {}

    public <CFG> Plugin<CFG> registerPlugin(Plugin<CFG> plugin) {
        // TODO implement
        return plugin;
    }

    public <T> void appData(String key, T value) {}

    public static void applyUserConfig(JavaFastState cfg, Consumer<JavaFastConfig> userConfig) {}
}
