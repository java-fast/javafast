package io.javafast.transport;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

final class SocketSelector implements Runnable {

    private static final long READ_TIMEOUT_NANOS = 30_000_000_000L; // 30 seconds

    private final Selector selector;
    private final TransportBuffer transportBuffer;
    private final ProtocolHandler protocolHandler;
    private final RequestDispatcher requestDispatcher;

    private final Map<SelectableChannel, SocketConnection> connections = new HashMap<>();
    private final Queue<SocketChannel> registrations = new ConcurrentLinkedQueue<>();

    SocketSelector(Selector selector, TransportBuffer transportBuffer, ProtocolHandler protocolHandler, RequestDispatcher requestDispatcher) {
        this.selector = selector;
        this.transportBuffer = transportBuffer;
        this.protocolHandler = protocolHandler;
        this.requestDispatcher = requestDispatcher;
    }

    void register(SocketChannel socket) {
        this.registrations.offer(socket);
        // TODO unnecessary wakeup under high connection rates?
        this.selector.wakeup();
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                registerPendingConnections();
                long now = System.nanoTime();
                final long timeout = computeSelectTimeout(now);
                final int ready;
                if (timeout == 0) {
                    ready = this.selector.selectNow();
                } else {
                    ready = this.selector.select(timeout);
                }
                now = System.nanoTime();
                registerPendingConnections();
                if (ready > 0) {
                    processSelectedKeys(now);
                }
                checkTimeouts(now);
            }
        } catch (IOException e) {
            throw new RuntimeException("Selector failed", e);
        }
    }

    private void registerPendingConnections() throws IOException {
        SocketChannel socket;
        while ((socket = this.registrations.poll()) != null) {
            socket.register(this.selector, SelectionKey.OP_READ);
            final SocketConnection connection = new SocketConnection(socket, this.protocolHandler, System.nanoTime());
            this.connections.put(socket, connection);
        }
    }

    private void processSelectedKeys(long now) {
        final Iterator<SelectionKey> iterator = this.selector.selectedKeys().iterator();
        while (iterator.hasNext()) {
            final SelectionKey key = iterator.next();
            iterator.remove();

            if (!key.isValid()) {
                continue;
            }
            if (!key.isReadable()) {
                continue;
            }

            final SocketConnection connection = this.connections.get(key.channel());
            if (connection == null) {
                close(key);
                continue;
            }

            try {
                final boolean requestComplete = connection.read(this.transportBuffer, now);
                if (requestComplete) {
                    // TransportBuffer ownership has now been transferred to the request processor
                    // Connection itself remains alive for HTTP keep-alive.
                    dispatch(connection.published);
                }
            } catch (IOException e) {
                close(key);
            }
        }
    }

    private void dispatch(TransportBuffer.Reader reader) {
        Thread.ofVirtual().start(() -> {
            try {
                this.requestDispatcher.dispatch(reader);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                reader.release();
            }
        });
    }

    private void checkTimeouts(long now) {
        // TODO convert PriorityQueue/deadline wheel?
        final var iterator = this.connections.entrySet().iterator();
        while (iterator.hasNext()) {
            final var entry = iterator.next();
            final SocketConnection connection = entry.getValue();
            final long idle = now - connection.lastReadNanos;
            if (idle >= READ_TIMEOUT_NANOS) {
                connection.close();
                iterator.remove();
            }
        }
    }

    private long computeSelectTimeout(long now) {
        long timeoutNanos = READ_TIMEOUT_NANOS;
        for (SocketConnection connection : this.connections.values()) {
            final long remaining = READ_TIMEOUT_NANOS - (now - connection.lastReadNanos);
            if (remaining < timeoutNanos) {
                timeoutNanos = Math.max(remaining, 0);
            }
        }
        return timeoutNanos / 1_000_000; // milliseconds?
    }

    private void close(SelectionKey key) {
        final SocketConnection connection = this.connections.remove(key.channel());
        key.cancel();
        if (connection != null) {
            connection.close();
        }
    }
}
