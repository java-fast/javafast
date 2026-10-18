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

    private volatile boolean stopping;

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
            while (!this.stopping && !Thread.currentThread().isInterrupted()) {
                final SocketChannel socket = this.serverChannel.accept(); // blocking
                if (this.stopping) {
                    socket.close();
                    break;
                }
                try {
                    configure(socket);
                    this.socketSelector.register(socket);
                } catch (Exception e) {
                    socket.close();
                    if (!this.stopping) {
                        throw e;
                    }
                }
            }
        } catch (Exception e) {
            if (!this.stopping) {
                throw new RuntimeException("Acceptor failed", e);
            }
        }
    }

    private static void configure(SocketChannel socket) throws IOException {
        socket.configureBlocking(false);
        socket.socket().setTcpNoDelay(true);
    }

    @Override
    public synchronized void stop() {
        if (this.stopping) {
            return;
        }
        this.stopping = true;

        IOException failure = null;
        // stop accepting new connections
        if (this.serverChannel != null) {
            try {
                this.serverChannel.close();
            } catch (IOException e) {
                failure = e;
            }
        }
        // wake up selector and request its termination
        if (this.socketSelector != null) {
            this.socketSelector.shutdown();
        }
        // ensure the blocking accept() exits
        if (this.acceptorThread != null) {
            this.acceptorThread.interrupt();
        }
        // wait for both threads to finish cleanup
        joinUninterruptibly(this.acceptorThread);
        joinUninterruptibly(this.selectorThread);
        if (failure != null) {
            throw new UncheckedIOException("Error stopping JdkHttpTransport", failure);
        }
    }

    private static void joinUninterruptibly(Thread thread) {
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        boolean interrupted = false;
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public int port() {
        return 0;
    }
}
