package io.javafast;

import io.javafast.config.JavaFastConfig;
import io.javafast.config.JavaFastState;
import io.javafast.transport.Transport;
import io.javafast.transport.TransportConfig;

import java.util.function.Consumer;

public class JavaFast {

    private final JavaFastState state;

    // underlying transport can be a servlet container or other transport
    private Transport transport;

    protected JavaFast(JavaFastState state) {
        this.state = state;
    }

    /**
     * Creates a new instance of JavaFast with the default configuration.
     * The server does not run until {@link #start()} is called.
     *
     * @return JavaFast instance
     * @see #create(Consumer)
     */
    public static JavaFast create() {
        return create(c -> {
        });
    }

    /**
     * Creates a new instance of JavaFast with custom configuration.
     * The server does not run until {@link #start()} is called.
     *
     * @param config Configuration consumer
     * @return JavaFast instance
     */
    public static JavaFast create(Consumer<JavaFastConfig> config) {
        final JavaFastState state = new JavaFastState();
        config.accept(new JavaFastConfig(state));
        return new JavaFast(state);
    }

    /**
     * Creates a new instance of JavaFast with custom configuration and starts the server.
     *
     * @param config Configuration consumer
     * @return JavaFast instance
     */
    public static JavaFast start(Consumer<JavaFastConfig> config) {
        return create(config).start();
    }

    public JavaFast start(String host, int port) {
        try {
            this.transport = this.state.transport.get();
            this.transport.start(new TransportConfig(host, port));
            return this;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public JavaFast start(int port) {
        return start(null, port);
    }

    public JavaFast start() {
        return start(null, 0);
    }

    public JavaFast stop() {
        this.transport.stop();
        return this;
    }

    public int port() {
        // in case the underlying transport determines a different port, like a random port, etc.
        return this.transport.port();
    }
}
