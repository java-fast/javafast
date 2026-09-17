package io.javafast.transport;

import com.sun.net.httpserver.HttpServer;
import io.javafast.runtime.ApplicationRuntime;
import io.javafast.JavaFastRuntime;
import io.javafast.http.HttpExchange;
import io.javafast.internal.DefaultHttpExchangeHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class JdkTestTransport implements Transport {

    private final ExchangeHandler<HttpExchange> handler;

    private HttpServer server;
    private TransportConfig config;

    public JdkTestTransport() {
        final ApplicationRuntime runtime = JavaFastRuntime.current();
        this.handler = new DefaultHttpExchangeHandler(runtime.routing(), runtime.appData());
    }

    private static class JdkHttpExchange implements HttpExchange {

        com.sun.net.httpserver.HttpExchange exchange;

        private JdkHttpExchange(com.sun.net.httpserver.HttpExchange exchange) {
            this.exchange = exchange;
        }

        @Override
        public String method() {
            return this.exchange.getRequestMethod();
        }

        @Override
        public String uri() {
            return this.exchange.getRequestURI().toString();
        }

        @Override
        public String header(String name) {
            return this.exchange.getRequestHeaders().getFirst(name);
        }

        @Override
        public InputStream requestBody() {
            return this.exchange.getRequestBody();
        }

        @Override
        public void status(int status) {
            try {
                this.exchange.sendResponseHeaders(status, 0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void header(String name, String value) {
            this.exchange.getResponseHeaders().set(name, value);
        }

        @Override
        public OutputStream responseBody() {
            return this.exchange.getResponseBody();
        }

        @Override
        public void close() {
            this.exchange.close();
        }
    }

    @Override
    public void start(TransportConfig config) {
        this.config = config;
        try {
            this.server = HttpServer.create(new InetSocketAddress(config.host() == null ? "localhost" : config.host(), config.port()), 0);
            // TODO for now single thread for optimizing the code
            this.server.setExecutor(Executors.newSingleThreadExecutor());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        this.server.createContext("/", exchange -> {
            final var adaptedExchange = new JdkHttpExchange(exchange);
            try {
                this.handler.handle(adaptedExchange);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        this.server.start();
    }

    @Override
    public void stop() {
        this.server.stop(0);
    }

    @Override
    public int port() {
        if (this.config != null) {
            return this.config.port();
        }
        throw new IllegalStateException("Server is not started");
    }

}
