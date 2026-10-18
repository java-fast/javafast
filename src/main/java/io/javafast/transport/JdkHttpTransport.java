package io.javafast.transport;

import io.javafast.JavaFastRuntime;
import io.javafast.internal.DefaultHttpRequestDispatcher;
import io.javafast.internal.RequestContextPool;
import io.javafast.runtime.ApplicationRuntime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public final class JdkHttpTransport implements Transport {

    private static final int REQUEST_POOL_SIZE = Integer.getInteger("javafast.request.pool.size", 1024); // max pre-allocated request contexts

    private final RequestContextPool requestContextPool = RequestContextPool.create(REQUEST_POOL_SIZE);
    private final ApplicationRuntime runtime;
    private final TransportBuffer transportBuffer;

    private ServerSocketChannel serverChannel;
    private Selector selector;

    private SocketSelector socketSelector;

    private Thread acceptorThread;
    private Thread selectorThread;

    public JdkHttpTransport(TransportBuffer transportBuffer) {
        this.runtime = JavaFastRuntime.current();
        this.transportBuffer = transportBuffer;
    }

    @Override
    public void start(TransportConfig config) {
        try {
            this.selector = Selector.open();
            this.socketSelector = new SocketSelector(
                this.selector,
                this.transportBuffer,
                new HttpProtocolHandler(), // instance per selector thread, runs in selector thread
                new DefaultHttpRequestDispatcher(this.requestContextPool, this.runtime.routing(), this.runtime.appData()) // instance per selector thread
            );
            this.serverChannel = ServerSocketChannel.open();
            this.serverChannel.configureBlocking(true);
            this.serverChannel.bind(new InetSocketAddress(config.host() == null ? "localhost" : config.host(), config.port()));
        } catch (IOException e) {
            throw new UncheckedIOException("Error starting JdkHttpTransport", e);
        }
        this.acceptorThread = Thread.ofPlatform().name("javafast-acceptor").start(this::acceptLoop);
        this.selectorThread = Thread.ofPlatform().name("javafast-selector").start(this.socketSelector);
    }

    private void acceptLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                final SocketChannel socket = this.serverChannel.accept(); // blocking
                configure(socket);
                this.socketSelector.register(socket);
            }
        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()) {
                throw new RuntimeException("Acceptor failed", e);
            }
        }
    }

    private static void configure(SocketChannel socket) throws IOException {
        socket.configureBlocking(false);
        socket.socket().setTcpNoDelay(true);
    }

    @Override
    public void stop() {
        this.acceptorThread.interrupt();
        this.selectorThread.interrupt();
        try {
            this.serverChannel.close();
            this.selector.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Error stopping JdkHttpTransport", e);
        }
    }

    @Override
    public int port() {
        return 0;
    }
}
