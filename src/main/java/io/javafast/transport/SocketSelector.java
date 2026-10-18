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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO also implement other timeouts:
 * - Request deadline
 * - Write timeout
 */
final class SocketSelector implements Runnable {

    private static final Logger log = Logger.getLogger(SocketSelector.class.getName());

    private static final long READ_TIMEOUT_NANOS = Long.getLong("javafast.read.timeout.nanos", 30_000_000_000L); // default 30 seconds

    private volatile boolean running = true;

    private final Selector selector;
    private final TransportBuffer transportBuffer;
    private final ProtocolHandler protocolHandler;
    private final RequestDispatcher requestDispatcher;

    private final Map<SelectableChannel, SocketConnection> connections = new HashMap<>();
    private final Queue<SocketChannel> registrations = new ConcurrentLinkedQueue<>();
    private final Queue<SocketConnection> writableSockets = new ConcurrentLinkedQueue<>();
    private final Queue<SocketConnection> errors = new ConcurrentLinkedQueue<>();

    SocketSelector(Selector selector, TransportBuffer transportBuffer, ProtocolHandler protocolHandler, RequestDispatcher requestDispatcher) {
        this.selector = selector;
        this.transportBuffer = transportBuffer;
        this.protocolHandler = protocolHandler;
        this.requestDispatcher = requestDispatcher;
    }

    void register(SocketChannel socket) {
        if (!this.running) {
            closeQuietly(socket);
            return;
        }
        this.registrations.offer(socket);
        // handle the race in shutdown
        if (!this.running && this.registrations.remove(socket)) {
            closeQuietly(socket);
            return;
        }
        // TODO unnecessary wakeup under high connection rates?
        this.selector.wakeup();
    }

    @Override
    public void run() {
        try {
            while (this.running && !Thread.currentThread().isInterrupted()) {
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
                processWritableSockets();
                checkSocketReadTimeouts(now);
            }
        } catch (Exception e) {
            throw new RuntimeException("Selector failed", e);
        } finally {
            closeResources();
        }
    }

    private void registerPendingConnections() throws IOException {
        SocketChannel socket;
        while ((socket = this.registrations.poll()) != null) {
            final SelectionKey key = socket.register(this.selector, SelectionKey.OP_READ);
            final SocketConnection connection = new SocketConnection(key, socket, this.protocolHandler, System.nanoTime());
            this.connections.put(socket, connection);
        }
    }

    private void processWritableSockets() {
        SocketConnection conn;
        while ((conn = this.writableSockets.poll()) != null) {
            final SelectionKey key = conn.key;
            if (key.isValid()) {
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE); // enable to write
            }
        }
    }

    private void processSelectedKeys(long now) {
        final Iterator<SelectionKey> iterator = this.selector.selectedKeys().iterator();
        SelectionKey key;
        SocketConnection conn;
        while (iterator.hasNext()) {
            key = iterator.next();
            iterator.remove();
            if (!key.isValid()) {
                continue;
            }
            if (key.isReadable()) {
                if ((conn = this.connections.get(key.channel())) == null) {
                    key.cancel();
                    continue;
                }
                processRead(conn, now);
                if (!key.isValid()) {
                    continue;
                }
            }
            if (key.isWritable()) {
                if ((conn = this.connections.get(key.channel())) == null) {
                    key.cancel();
                    continue;
                }
                processWrite(conn, now);
            }
        }
    }

    private void processRead(SocketConnection conn, long now) {
        try {
            if (conn.read(this.transportBuffer, now)) {
                // TransportBuffer ownership has now been transferred to the request processor
                dispatch(conn);
            }
        } catch (IOException _) {
            close(conn);
        }
    }

    private void processWrite(SocketConnection conn, long now) {
        while (true) {
            if (conn.response == null) {
                conn.response = conn.responseBuffer.take();
                if (conn.response == null) { // no response ready
                    conn.key.interestOps(conn.key.interestOps() & ~SelectionKey.OP_WRITE); // disable write
                    return;
                }
            }
            final var reader = conn.response.reader();
            try {
                conn.write(reader, now);
                if (!reader.hasRemaining()) {
                    final long sequence = conn.response.sequence();
                    reader.close(); // release blocks
                    conn.response = null;
                    conn.responseBuffer.update(sequence, ResponseBuffer.SlotState.FREE);
                    continue;
                }
            } catch (IOException _) {
                close(conn);
                return;
            }
        }
    }

    private void dispatch(SocketConnection conn) {
        final long sequence = conn.publishedSequence;
        final TransportBuffer.Writer writer = conn.published;
        Thread.ofVirtual().start(() -> {
            try {
                this.requestDispatcher.dispatch(writer);
            } catch (Exception e) {
                log.log(Level.SEVERE, e.getMessage(), e);
                this.errors.offer(conn); // TODO update and release later
            } finally {
                if (conn.responseBuffer.update(sequence, ResponseBuffer.SlotState.READY)) {
                    this.writableSockets.offer(conn);
                    this.selector.wakeup();
                }
            }
        });
    }

    private void checkSocketReadTimeouts(long now) {
        // TODO convert PriorityQueue/deadline wheel?
        final var iterator = this.connections.entrySet().iterator();
        while (iterator.hasNext()) {
            final var entry = iterator.next();
            final SocketConnection connection = entry.getValue();
            final long idle = now - connection.lastReadNanos;
            if (idle >= READ_TIMEOUT_NANOS) {
                connection.key.cancel();
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
        // round up
        return (timeoutNanos == 0) ? 0 : ((timeoutNanos + 999_999) / 1_000_000);
    }

    void shutdown() {
        this.running = false;
        this.selector.wakeup();
    }

    private void close(SocketConnection connection) {
        connection.key.cancel();
        connection.close();
    }

    private void closeQuietly(SocketChannel socket) {
        try {
            socket.close();
        } catch (IOException _) {
            // best effort shutdown
        }
    }

    private void closeResources() {
        // all registered connections are cleared
        this.connections.values().forEach(this::close);
        this.connections.clear();
        // close accepted sockets that not yet registered with the selector
        SocketChannel socket;
        while ((socket = this.registrations.poll()) != null) {
            closeQuietly(socket);
        }
        this.writableSockets.clear();
        this.errors.clear();
        try {
            this.selector.close();
        } catch (IOException _) {
            // best effort shutdown
        }
    }

}
