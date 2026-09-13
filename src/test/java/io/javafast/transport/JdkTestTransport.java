package io.javafast.transport;

import com.sun.net.httpserver.HttpServer;
import io.javafast.http.HttpExchange;
import io.javafast.internal.DefaultHttpExchangeHandler;

public class JdkTestTransport implements Transport {

    private final ExchangeHandler<HttpExchange> handler = new DefaultHttpExchangeHandler();

    private HttpServer server;
    private TransportConfig config;

    @Override
    public void start(TransportConfig config) {
        this.config = config;
        // create HttpServer
        // Adapt request to HttpExchange
        // handler.handle(exchange)
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
