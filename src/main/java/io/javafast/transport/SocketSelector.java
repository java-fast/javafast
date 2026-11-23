package io.javafast.transport;

import io.javafast.transport.metrics.SelectorMetrics;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO also implement other timeouts:
 * - Request deadline
 * - Write timeout
 */
final class SocketSelector implements Runnable {

    private static final Logger log = Logger.getLogger(SocketSelector.class.getName());

    private static final int DISPATCHER_POOL_SIZE = Integer.getInteger("javafast.selector.dispatcher.pool.size", 128);
    private static final long READ_IDLE_TIMEOUT_NANOS = Long.getLong("javafast.read.timeout.nanos", TimeUnit.SECONDS.toNanos(10)); // default 10 seconds
    private static final long TIMEOUT_SCAN_INTERVAL_NANOS = 1_000_000_000L; // min socket read timeout is 1 second

    private static final int MAX_REQUESTS_PER_TURN = 16;
    private static final int MAX_RESPONSES_PER_TURN = 16;
    private static final int MAX_PENDING_READS_PER_TURN = 16;
    private static final int MAX_WRITABLE_CONNECTIONS_PER_TURN = 64;
    private static final int TIMEOUT_WHEEL_SIZE = 64;
    private static final long TIMEOUT_TICK_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final SelectorMetrics metrics = SelectorMetrics.create();
    private final Selector selector;
    private final TransportBuffer transportBuffer;
    private final Supplier<ProtocolHandler> protocolHandlerSupplier;
    private final RequestDispatcher requestDispatcher;

    private final WorkerPool workerPool = new WorkerPool(DISPATCHER_POOL_SIZE, this.metrics);
    private final ReadTimeoutWheel readTimeoutWheel = new ReadTimeoutWheel(TIMEOUT_WHEEL_SIZE, TIMEOUT_TICK_NANOS, System.nanoTime());
    private final List<SocketConnection> connections = new ArrayList<>();
    private final Queue<SocketChannel> registrations = new ConcurrentLinkedQueue<>();
    private final Queue<SocketConnection> errors = new ConcurrentLinkedQueue<>();

    private static final byte[] INTERNAL_SERVER_ERROR_RESPONSE =
        "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 21\r\nConnection: close\r\n\r\nInternal Server Error"
            .getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private final AtomicBoolean wakeupPending = new AtomicBoolean();
    private volatile boolean running = true;

    SocketSelector(Selector selector, TransportBuffer transportBuffer, Supplier<ProtocolHandler> protocolHandlerFactory, RequestDispatcher requestDispatcher) {
        this.selector = selector;
        this.transportBuffer = transportBuffer;
        this.protocolHandlerSupplier = protocolHandlerFactory;
        this.requestDispatcher = requestDispatcher;
    }

    private static void enableWrite(SocketConnection conn) {
        final SelectionKey key = conn.key;
        if (!key.isValid()) {
            return;
        }
        final int ops = key.interestOps();
        if ((ops & SelectionKey.OP_WRITE) == 0) {
            key.interestOps(ops | SelectionKey.OP_WRITE);
        }
    }

    private static void disableWrite(SocketConnection conn) {
        final SelectionKey key = conn.key;
        if (!key.isValid()) {
            return;
        }
        final int ops = key.interestOps();
        if ((ops & SelectionKey.OP_WRITE) != 0) {
            key.interestOps(ops & ~SelectionKey.OP_WRITE);
        }
    }

    private void signalSelector() {
        if (this.wakeupPending.compareAndSet(false, true)) {
            this.metrics.incrementResponseWakeupCalls();
            this.selector.wakeup();
        }
    }

    /**
     * Accessed by Acceptor
     */
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
        this.metrics.incrementRegistrationWakeupCalls();
        this.selector.wakeup();
    }

    @Override
    public void run() {
        try {
            long now = System.nanoTime();
            long nextTimeoutScan = now + TIMEOUT_SCAN_INTERVAL_NANOS;
            this.metrics.startReporting(now);
            while (this.running && !Thread.currentThread().isInterrupted()) {
                final long loopStart = System.nanoTime();
                now = loopStart;
                registerPendingConnections(now);
                processPendingReads(now);

                final int keysConsumed;
                final long timeout = computeSelectTimeout(now, nextTimeoutScan);
                final long start = this.metrics.start();
                if (timeout == 0) {
                    keysConsumed = this.selector.selectNow(key -> processSelectedKey(key, loopStart));
                    this.metrics.selectNowFinished(start);
                } else {
                    keysConsumed = this.selector.select(key -> processSelectedKey(key, loopStart), timeout);
                    this.metrics.selectFinished(start);
                }
                this.metrics.incrementSelectedKeysCount(keysConsumed);
                this.metrics.selectedKeysFinished(start);

                now = System.nanoTime();
                registerPendingConnections(now);
                processWritableSockets(now);
                if (now - nextTimeoutScan >= 0) {
                    checkSocketReadTimeouts(now);
                    nextTimeoutScan = now + TIMEOUT_SCAN_INTERVAL_NANOS;
                }

                this.metrics.loopFinished(loopStart);
                this.metrics.report();
            }
        } catch (Exception e) {
            throw new RuntimeException("Selector failed", e);
        } finally {
            closeResources();
        }
    }

    private void registerPendingConnections(long now) throws IOException {
        SocketChannel socket;
        while ((socket = this.registrations.poll()) != null) {
            final SelectionKey key = socket.register(this.selector, SelectionKey.OP_READ, null);
            final SocketConnection conn = new SocketConnection(key, socket, this.protocolHandlerSupplier.get(), this.readTimeoutWheel, now);
            conn.index = this.connections.size();
            key.attach(conn);
            this.connections.add(conn);
            conn.readDeadlineNanos = conn.lastReadNanos + READ_IDLE_TIMEOUT_NANOS;
            this.readTimeoutWheel.add(conn);
        }
    }

    private void processPendingReads(long now) {
        for (int i = 0; i < MAX_PENDING_READS_PER_TURN; i++) {
            final SocketConnection conn = pollReadPending();
            if (conn == null) {
                return;
            }
            if (conn.isClosed()) {
                continue;
            }
            processRead(conn, now);
        }
    }

    private void processWritableSockets(long now) {
        int processed = 0;
        SocketConnection conn;
        while (processed < MAX_WRITABLE_CONNECTIONS_PER_TURN && (conn = pollWritable()) != null) {
            processWrite(conn, now);
            processed++;
        }
        // queue still contains work, computeSelectTimeout() will return 0 on the next loop
        if (hasWritable()) {
            return;
        }
        this.wakeupPending.set(false);
        // handle the race in between above wakeupPending.set(false)
        conn = pollWritable();
        if (conn != null) {
            processWrite(conn, now);
        }
    }

    private synchronized boolean hasWritable() {
        return this.writableHead != null;
    }

    private void processSelectedKey(SelectionKey key, long now) {
        this.metrics.incrementProcessedKeysCount();
        if (!key.isValid()) {
            return;
        }
        this.metrics.incrementValidKeysCount();
        final SocketConnection conn = (SocketConnection) key.attachment();
        if (conn == null) {
            key.cancel();
            return;
        }
        if (key.isReadable()) {
            processRead(conn, now);
            if (!key.isValid()) {
                return;
            }
        }
        if (key.isWritable()) {
            processWrite(conn, now);
        }
    }

    private void processRead(SocketConnection conn, long now) {
        try {
            int requests = 0;
            while (requests < MAX_REQUESTS_PER_TURN) {
                long start = this.metrics.start();
                final boolean ready;
                try {
                    ready = conn.read(this.transportBuffer, now);
                } finally {
                    this.metrics.readFinished(start);
                    this.metrics.bytesRead(conn.readBytesThisCall);
                }
                if (!ready) {
                    break;
                }
                this.metrics.incrementReadRequests();
                start = this.metrics.start();
                dispatch(conn);
                requests++;
                this.metrics.dispatchFinished(start);
            }
            if (conn.pendingBytes() > 0) { // still have written bytes
                enqueueReadPending(conn); // enqueue for next selector loop
            }
        } catch (IOException _) {
            close(conn);
        }
    }

    private void processWrite(SocketConnection conn, long now) {
        int responses = 0;
        while (responses < MAX_RESPONSES_PER_TURN) {
            if (conn.response == null) {
                conn.response = conn.responseBuffer.take();
                if (conn.response == null) { // no response ready
                    disableWrite(conn);
                    return;
                }
            }
            final var reader = conn.response.writer().reader();
            try {
                final long start = this.metrics.start();
                final int written = conn.write(reader, now);
                this.metrics.writeFinished(start);
                this.metrics.bytesWritten(written);
                if (reader.hasRemaining()) {
                    enableWrite(conn); // socket buffer full, wait for next writable event
                    return;
                }
                final long sequence = conn.response.sequence();
                reader.close(); // release blocks
                conn.response = null;
                conn.responseBuffer.update(sequence, ResponseBuffer.SlotState.FREE);
                responses++;
                this.metrics.incrementCompletedRequests();
            } catch (IOException _) {
                close(conn);
                return;
            }
        }
        // fairness limit reached, continue in the next selector loop, without OP_WRITE
        disableWrite(conn);
        enqueueWritable(conn);
    }

    private void dispatch(SocketConnection conn) {
        final long sequence = conn.publishedSequence;
        final TransportBuffer.Writer writer = conn.published;
        this.workerPool.submit(() -> {
            boolean responseReady = false;
            try {
                // TransportBuffer ownership has now been transferred to the request processor
                this.requestDispatcher.dispatch(writer);
                responseReady = true;
            } catch (Throwable e) {
                log.log(Level.SEVERE, e.getMessage(), e);
                responseReady = writeInternalServerError(writer);
            } finally {
                if (responseReady) {
                    if (conn.responseBuffer.update(sequence, ResponseBuffer.SlotState.READY)) {
                        if (enqueueWritable(conn)) {
                            signalSelector();
                        }
                    }
                } else { // enqueue for errors so selector can close connection later
                    this.errors.offer(conn);
                    // TODO also we need an abort/cancel mechanism for ResponseBuffer slot/state
                }
            }
        });
    }

    private static boolean writeInternalServerError(TransportBuffer.Writer writer) {
        try {
            final var reader = writer.reader();
            reader.reset();
            reader.limit(-1);
            writer.reset();
            writer.writeBytes(INTERNAL_SERVER_ERROR_RESPONSE);
            return true;
        } catch (Throwable error) {
            try { // best-effort release in case of an error
                writer.release();
            } catch (Throwable releaseError) {
                error.addSuppressed(releaseError);
            }
            return false;
        }
    }

    private void checkSocketReadTimeouts(long now) {
        this.readTimeoutWheel.expire(now, conn -> {
            conn.close();
            removeConnection(conn);
        });
    }

    private long computeSelectTimeout(long now, long nextTimeoutScan) {
        if (this.readPendingHead != null || hasWritable()) {
            return 0;
        }
        if (this.connections.isEmpty()) {
            return READ_IDLE_TIMEOUT_NANOS / 1_000_000L;
        }
        final long timeoutNanos = Math.max(0L, nextTimeoutScan - now);
        return (timeoutNanos == 0) ? 0 : ((timeoutNanos + 999_999L) / 1_000_000L); // round up
    }

    void shutdown() {
        this.running = false;
        this.selector.wakeup();
    }

    /**
     * Swap-remove: efficiently removes a connection from the ArrayList with O(1)
     */
    private void removeConnection(SocketConnection conn) {
        final int index = conn.index;
        if (index < 0) {
            return;
        }
        final int lastIndex = this.connections.size() - 1;
        if (index != lastIndex) {
            final SocketConnection last = this.connections.get(lastIndex);
            this.connections.set(index, last);
            last.index = index;
        }
        this.connections.remove(lastIndex);
        conn.index = -1;
    }

    private void close(SocketConnection connection) {
        this.readTimeoutWheel.remove(connection);
        removeConnection(connection);
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
        this.connections.forEach(SocketConnection::close);
        this.connections.clear();
        // close accepted sockets that not yet registered with the selector
        SocketChannel socket;
        while ((socket = this.registrations.poll()) != null) {
            closeQuietly(socket);
        }
        while (pollReadPending() != null) {
            // drain read queue
        }
        while (pollWritable() != null) {
            // drain writable queue
        }
        try {
            this.selector.close();
        } catch (IOException _) {
            // best effort shutdown
        }
    }

    private SocketConnection readPendingHead;
    private SocketConnection readPendingTail;
    private SocketConnection writableHead;
    private SocketConnection writableTail;

    private void enqueueReadPending(SocketConnection conn) {
        if (conn.readPendingQueued) {
            return;
        }
        conn.readPendingQueued = true;
        conn.readPendingNext = null;
        if (this.readPendingTail == null) {
            this.readPendingHead = conn;
        } else {
            this.readPendingTail.readPendingNext = conn;
        }
        this.readPendingTail = conn;
    }

    private SocketConnection pollReadPending() {
        final SocketConnection conn = this.readPendingHead;
        if (conn == null) {
            return null;
        }
        this.readPendingHead = conn.readPendingNext;
        if (this.readPendingHead == null) {
            this.readPendingTail = null;
        }
        conn.readPendingNext = null;
        conn.readPendingQueued = false;
        return conn;
    }

    private synchronized boolean enqueueWritable(SocketConnection conn) {
        if (conn.writableQueued) {
            return false;
        }
        conn.writableQueued = true;
        conn.writableNext = null;
        if (this.writableTail == null) {
            this.writableHead = conn;
        } else {
            this.writableTail.writableNext = conn;
        }
        this.writableTail = conn;
        return true;
    }

    private synchronized SocketConnection pollWritable() {
        final SocketConnection conn = this.writableHead;
        if (conn == null) {
            return null;
        }
        this.writableHead = conn.writableNext;
        if (this.writableHead == null) {
            this.writableTail = null;
        }
        conn.writableNext = null;
        conn.writableQueued = false;
        return conn;
    }

}
