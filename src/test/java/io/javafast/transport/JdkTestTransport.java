package io.javafast.transport;

import com.sun.net.httpserver.HttpServer;
import io.javafast.JavaFastRuntime;
import io.javafast.internal.DefaultHttpRequestDispatcher;
import io.javafast.internal.RequestContextPool;
import io.javafast.runtime.ApplicationRuntime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class JdkTestTransport implements Transport {

    private final TransportBuffer buffer;
    private final RequestDispatcher handler;
    private final HttpTestRequestEncoder encoder = new HttpTestRequestEncoder();
    private long nextRequestSequence;

    private HttpServer server;
    private TransportConfig config;

    public JdkTestTransport() {
        final ApplicationRuntime runtime = JavaFastRuntime.current();
        this.handler = new DefaultHttpRequestDispatcher(RequestContextPool.create(128), runtime.routing(), runtime.appData());
        this.buffer = TransportBuffer.create(1024, 1024); // aka 1MB
    }

    @Override
    public void start(TransportConfig config) {
        this.config = config;
        try {
            this.server = HttpServer.create(new InetSocketAddress(config.host() == null ? "localhost" : config.host(), config.port()), 0);
            this.server.setExecutor(Executors.newSingleThreadExecutor());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        this.server.createContext("/", exchange -> {
            TransportBuffer.Writer writer = null;
            try {
                writer = this.encoder.encode(exchange, this.buffer);
                this.handler.dispatch(this.nextRequestSequence++, writer.reader());
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                if (writer != null) {
                    writer.release();
                }
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
