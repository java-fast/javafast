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
import java.util.concurrent.atomic.AtomicLong;
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
    private static final long TIMEOUT_SCAN_INTERVAL_NANOS = 1_000_000_000L; // min socket read timeout is 1 second
    private static final int MAX_REQUESTS_PER_TURN = 16;

    private volatile boolean running = true;

    private final Selector selector;
    private final TransportBuffer transportBuffer;
    private final ProtocolHandler protocolHandler;
    private final RequestDispatcher requestDispatcher;

    private final Map<SelectableChannel, SocketConnection> connections = new HashMap<>();
    private final Queue<SocketChannel> registrations = new ConcurrentLinkedQueue<>();
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
        this.registrationWakeups.incrementAndGet();
        this.selector.wakeup();
    }

    /**
     * Instrumentation for benchmarking
     */
    private long loopCount;
    private long loopNanos;
    private long selectCount;
    private long selectNanos;
    private long selectNowCount;
    private long selectNowNanos;
    private long selectedKeysNanos;
    private long selectedKeysCount;
    private long processedKeysCount;
    private long validKeysCount;
    private long completedRequests;
    private final AtomicLong registrationWakeups = new AtomicLong();
    private final AtomicLong responseWakeups = new AtomicLong();

    @Override
    public void run() {
        try {
            long nextTimeoutScan = System.nanoTime() + TIMEOUT_SCAN_INTERVAL_NANOS;
            long nextReport = System.nanoTime() + 1_000_000_000L;
            while (this.running && !Thread.currentThread().isInterrupted()) {
                final long loopStart = System.nanoTime();

                registerPendingConnections();
                long now = System.nanoTime();
                final long timeout = computeSelectTimeout(now, nextTimeoutScan);
                final int ready;

                final long start = System.nanoTime();
                if (timeout == 0) {
                    ready = this.selector.selectNow();
                    this.selectNowNanos += System.nanoTime() - start;
                    this.selectNowCount++;
                } else {
                    ready = this.selector.select(timeout);
                    this.selectNanos += System.nanoTime() - start;
                    this.selectCount++;
                }

                now = System.nanoTime();
                registerPendingConnections();
                if (ready > 0) {
                    this.selectedKeysCount += ready;
                    processSelectedKeys(now);
                }
                processWritableSockets();
                if (now - nextTimeoutScan >= 0) {
                    checkSocketReadTimeouts(now);
                    nextTimeoutScan = now + TIMEOUT_SCAN_INTERVAL_NANOS;
                }

                this.loopCount++;
                this.loopNanos += System.nanoTime() - loopStart;
                if (now - nextReport >= 0) {
//                    reportStats();
                    resetStats();
                    nextReport = now + 1_000_000_000L;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Selector failed", e);
        } finally {
            closeResources();
        }
    }

    private void resetStats() {
        this.loopCount = 0;
        this.loopNanos = 0;
        this.selectCount = 0;
        this.selectNanos = 0;
        this.selectNowCount = 0;
        this.selectNowNanos = 0;
        this.selectedKeysCount = 0;
        this.processedKeysCount = 0;
        this.selectedKeysNanos = 0;
        this.completedRequests = 0;
        this.validKeysCount = 0;

        this.registrationWakeups.set(0);
        this.responseWakeups.set(0);
    }

    private void reportStats() {
        final long requests = this.completedRequests;
        final long loops = this.loopCount;
        final long totalWakeups =
            this.registrationWakeups.get() + this.responseWakeups.get();

        final double avgLoopNanos =
            loops == 0 ? 0 : (double) this.loopNanos / loops;

        final double avgSelectNanos =
            this.selectCount == 0 ? 0 : (double) this.selectNanos / this.selectCount;

        final double avgSelectNowNanos =
            this.selectNowCount == 0 ? 0 : (double) this.selectNowNanos / this.selectNowCount;

        final double avgRequestsPerLoop =
            loops == 0 ? 0 : (double) requests / loops;

        final double wakeupsPerRequest =
            requests == 0 ? 0 : (double) this.responseWakeups.get() / requests;

        final double avgSelectedKeysPerLoop =
            loops == 0 ? 0 : (double) this.selectedKeysCount / loops;

        final double avgProcessedKeysPerLoop =
            loops == 0 ? 0 : (double) this.processedKeysCount / loops;

        final double avgValidKeysPerLoop =
            loops == 0 ? 0 : (double) this.validKeysCount / loops;

        final double selectedProcessingPercent =
            this.loopNanos == 0 ? 0 :
                100.0 * this.selectedKeysNanos / this.loopNanos;

        final double selectPercent =
            this.loopNanos == 0 ? 0 :
                100.0 * (this.selectNanos + this.selectNowNanos) / this.loopNanos;

        final double otherProcessingPercent =
            100.0 - selectedProcessingPercent - selectPercent;

        log.info(() -> String.format(
            """
            SelectorStats:
              loops/sec:                  %,d
              avg loop:                   %.1f ns
              select calls/sec:           %,d
              avg select:                 %.1f ns
              selectNow calls/sec:        %,d
              avg selectNow:              %.1f ns
              selected keys/sec:          %,d
              processed keys/sec:         %,d
              valid keys/sec:             %,d
              selected keys/loop:         %.3f
              processed keys/loop:        %.3f
              valid keys/loop:            %.3f
              selected processing:        %.2f%%
              select time:                %.2f%%
              other  time:                %.2f%%
              requests/sec:               %,d
              requests/loop:              %.3f
              response wakeups/sec:       %,d
              registration wakeups/sec:   %,d
              wakeups/request:            %.3f
              total wakeups/sec:          %,d
            """,
            loops,
            avgLoopNanos,
            this.selectCount,
            avgSelectNanos,
            this.selectNowCount,
            avgSelectNowNanos,
            this.selectedKeysCount,
            this.processedKeysCount,
            this.validKeysCount,
            avgSelectedKeysPerLoop,
            avgProcessedKeysPerLoop,
            avgValidKeysPerLoop,
            selectedProcessingPercent,
            selectPercent,
            otherProcessingPercent,
            requests,
            avgRequestsPerLoop,
            this.responseWakeups.get(),
            this.registrationWakeups.get(),
            wakeupsPerRequest,
            totalWakeups
        ));
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
        while ((conn = pollWritable()) != null) {
            final SelectionKey key = conn.key;
            if (key.isValid()) {
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE); // enable to write
            }
        }
    }

    private void processSelectedKeys(long now) {
        final long start = System.nanoTime();

        try {
            final Iterator<SelectionKey> iterator = this.selector.selectedKeys().iterator();
            SelectionKey key;
            SocketConnection conn;
            while (iterator.hasNext()) {
                key = iterator.next();
                iterator.remove();
                this.processedKeysCount++;

                if (!key.isValid()) {
                    continue;
                }
                this.validKeysCount++;

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
        } finally {
            this.selectedKeysNanos += System.nanoTime() - start;
        }
    }

    private void processRead(SocketConnection conn, long now) {
        try {
            int requests = 0;
            while (requests < MAX_REQUESTS_PER_TURN) {
                if (!conn.read(this.transportBuffer, now)) {
                    break;
                }
                dispatch(conn);
                requests++;
            }
        } catch (IOException _) {
            closeAndRemove(conn);
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
                    this.completedRequests++;
                }
            } catch (IOException _) {
                closeAndRemove(conn);
                return;
            }
        }
    }

    private void dispatch(SocketConnection conn) {
        final long sequence = conn.publishedSequence;
        final TransportBuffer.Writer writer = conn.published;
        // FIXME: run in VT!
        try {
            // TransportBuffer ownership has now been transferred to the request processor
            this.requestDispatcher.dispatch(writer);
        } catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
            this.errors.offer(conn); // TODO update and release later
        } finally {
            if (conn.responseBuffer.update(sequence, ResponseBuffer.SlotState.READY)) {
                if (enqueueWritable(conn)) {
                    this.responseWakeups.incrementAndGet();
                    this.selector.wakeup();
                }
            }
        }
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

    private long computeSelectTimeout(long now, long nextTimeoutScan) {
        if (this.connections.isEmpty()) {
            return READ_TIMEOUT_NANOS / 1_000_000L;
        }
        final long timeoutNanos = Math.max(0L, nextTimeoutScan - now);
        // round up
        return (timeoutNanos == 0) ? 0 : ((timeoutNanos + 999_999L) / 1_000_000L);
    }

    void shutdown() {
        this.running = false;
        this.selector.wakeup();
    }

    private void close(SocketConnection connection) {
        connection.key.cancel();
        connection.close();
    }

    private void closeAndRemove(SocketConnection connection) {
        this.connections.remove(connection.key.channel());
        close(connection);
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
        while (pollWritable() != null) {
            // drain writable queue
        }
        this.errors.clear();
        try {
            this.selector.close();
        } catch (IOException _) {
            // best effort shutdown
        }
    }

    private SocketConnection writableHead;
    private SocketConnection writableTail;

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
