package io.javafast;

import io.javafast.config.JavaFastConfig;
import io.javafast.config.JavaFastState;

import java.util.function.Consumer;

public class JavaFast {

    private JavaFastState unsafe;

    // public JettyServer jettyServer() {}

    public static JavaFast create() {
        // TODO implement
        return new JavaFast();
    }

    public static JavaFast create(Consumer<JavaFastConfig> config) {
        // TODO implement
        return new JavaFast();
    }

    public static JavaFast start(Consumer<JavaFastConfig> config) {
        // TODO implement
        return new JavaFast();
    }

    // public Servlet javalinServlet() {}

    public JavaFast start(String host, int port) {
        // TODO implement
        return new JavaFast();
    }

    public JavaFast start(int port) {
        // TODO implement
        return new JavaFast();
    }

    public JavaFast start() {
        // TODO implement
        return new JavaFast();
    }

    public JavaFast stop() {
        // TODO implement
        return new JavaFast();
    }

    public int port() {
        // TODO get from underlying servlet container?
        return 0;
    }
}
