package io.javafast;

import io.javafast.config.JavaFastConfig;
import io.javafast.config.JavaFastState;

import java.util.function.Consumer;

public class JavaFast {

    private final JavaFastState state;

    protected JavaFast(JavaFastState state) {
        this.state = state;
    }

    /**
     * Creates a new instance of JavaFast with default configuration.
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
        // TODO implement
        return this;
    }

    public JavaFast start(int port) {
        // TODO set port
        return this;
    }

    public JavaFast start() {
        return this;
    }

    public JavaFast stop() {
        // TODO implement
        return this;
    }

    public int port() {
        // TODO get from underlying servlet container?
        return 0;
    }
}
