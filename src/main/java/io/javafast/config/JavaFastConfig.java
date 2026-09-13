package io.javafast.config;

import io.javafast.json.JsonMapper;
import io.javafast.plugin.Plugin;

/**
 * Public-facing configuration API exposed to users in [JavaFast.create].
 * This class provides a safe subset of configuration options, hiding internal APIs.
 * <p>
 * For advanced/dangerous configuration options, use [JavaFast.unsafe] to access [JavaFastState] directly.
 *
 * @see JavaFastState
 */
public class JavaFastConfig {

    private final JavaFastState state;

    // CORE CONFIGS - HTTP, routing, transport
    public final HttpConfig http;
    public final RouterConfig router;
    public final TransportFactoryConfig transport;

    // FEATURE CONFIGS - routes
    public final RoutesConfig routes;

    // CROSS-CUTTING CONFIGS - Validation, context resolution, logging, plugins, events
    public final ValidationConfig validation;
    public final ContextResolverConfig contextResolver;

    // MISC SETTINGS - General application-level settings
    public final StartupConfig startup;
    public final ConcurrencyConfig concurrency;

    public JavaFastConfig(JavaFastState state) {
        this.state = state;
        this.http = state.http;
        this.router = state.router;
        this.transport = state.transport;
        this.routes = state.routes;
        this.validation = state.validation;
        this.contextResolver = state.contextResolver;
        this.startup = state.startup;
        this.concurrency = state.concurrency;
    }

    public void jsonMapper(JsonMapper jsonMapper) {
    }

    public <CFG> void registerPlugin(Plugin<CFG> plugin) {
    }

    public <T> void appData(String key, T value) {
    }
}
