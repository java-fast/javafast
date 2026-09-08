package io.javafast.config;

import io.javafast.json.JsonMapper;
import io.javafast.plugin.Plugin;

/**
 * Public-facing configuration API exposed to users in [Javalin.create].
 * This class provides a safe subset of configuration options, hiding internal APIs.
 * <p>
 * For advanced/dangerous configuration options, use [Javalin.unsafe] to access [JavaFastState] directly.
 *
 * @see JavaFastState
 */
public class JavaFastConfig {

    private final JavaFastState state;

    // CORE CONFIGS - HTTP, routing, and server
    public final HttpConfig http;
    public final RouterConfig router;
    // public final JettyConfig jetty;

    // FEATURE CONFIGS - Static files, SPAs, and routes
//    public final RoutesConfig routes;

    // CROSS-CUTTING CONFIGS - Validation, context resolution, logging, plugins, events
    public final ValidationConfig validation;
    public final ContextResolverConfig contextResolver;
    public final EventConfig events;

    // MISC SETTINGS - General application-level settings
    public final StartupConfig startup;
    public final ConcurrencyConfig concurrency;

    public JavaFastConfig(JavaFastState state) {
        this.state = state;
        this.http = state.http;
        this.router = state.router;
        this.validation = state.validation;
        this.contextResolver = state.contextResolver;
        this.events = state.events;
        this.startup = state.startup;
        this.concurrency = state.concurrency;
    }

//    @SafeVarargs
//    public final void requestLifeCycle(TaskInitializer<JavalinServletContext>... requestLifecycle) {}

    public void jsonMapper(JsonMapper jsonMapper) {}
    public <CFG> void registerPlugin(Plugin<CFG> plugin) {}
    public <T> void appData(String key, T value) {}
}
