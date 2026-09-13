package io.javafast.transport;

import com.sun.net.httpserver.HttpServer;
import io.javafast.http.HttpExchange;
import io.javafast.internal.DefaultHttpExchangeHandler;
import io.javafast.internal.DefaultRouting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;

public class JdkTestTransport implements Transport {

    private final ExchangeHandler<HttpExchange> handler;

    private HttpServer server;
    private TransportConfig config;

    public JdkTestTransport(DefaultRouting routing) {
        this.handler = new DefaultHttpExchangeHandler(routing);
    }

    @Override
    public void start(TransportConfig config) {
        this.config = config;
        try {
            this.server = HttpServer.create(new InetSocketAddress(config.host() == null ? "localhost": config.host(), config.port()), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        this.server.createContext("/", exchange -> {
            final var adaptedExchange = new HttpExchange() {
                @Override
                public String method() {
                    return exchange.getRequestMethod();
                }

                @Override
                public String uri() {
                    return exchange.getRequestURI().toString();
                }

                @Override
                public String header(String name) {
                    return exchange.getRequestHeaders().getFirst(name);
                }

                @Override
                public InputStream requestBody() {
                    return exchange.getRequestBody();
                }

                @Override
                public void status(int status) {
                    try {
                        exchange.sendResponseHeaders(status, 0);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                @Override
                public void header(String name, String value) {
                    exchange.getResponseHeaders().set(name, value);
                }

                @Override
                public OutputStream responseBody() {
                    return exchange.getResponseBody();
                }

                @Override
                public void close() {
                    exchange.close();
                }
            };

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
