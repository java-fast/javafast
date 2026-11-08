package io.javafast.transport;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO also implement other timeouts:
 * - Request deadline
 * - Write timeout
 */
final class SocketSelector implements Runnable {

    private static final Logger log = Logger.getLogger(SocketSelector.class.getName());
    private static final boolean INSTRUMENTATION_ENABLED = Boolean.getBoolean("javafast.instrumentation");
    static final long READ_IDLE_TIMEOUT_NANOS = Long.getLong("javafast.read.timeout.nanos", TimeUnit.SECONDS.toNanos(10)); // default 10 seconds

    private static final long TIMEOUT_SCAN_INTERVAL_NANOS = 1_000_000_000L; // min socket read timeout is 1 second
    private static final int MAX_REQUESTS_PER_TURN = 16;
    private static final int MAX_RESPONSES_PER_TURN = 16;
    private static final int MAX_PENDING_READS_PER_TURN = 16;
    private static final int TIMEOUT_WHEEL_SIZE = 64;
    private static final long TIMEOUT_TICK_NANOS = TimeUnit.SECONDS.toNanos(1);

    final SocketMetrics metrics = INSTRUMENTATION_ENABLED ? new SocketMetrics() : null;
    private final Selector selector;
    private final TransportBuffer transportBuffer;
    private final ProtocolHandler protocolHandler;
    private final RequestDispatcher requestDispatcher;

    private final ReadTimeoutWheel readTimeoutWheel = new ReadTimeoutWheel(TIMEOUT_WHEEL_SIZE, TIMEOUT_TICK_NANOS, System.nanoTime());
    private final List<SocketConnection> connections = new ArrayList<>();
    private final Queue<SocketChannel> registrations = new ConcurrentLinkedQueue<>();
    private final Queue<SocketConnection> errors = new ConcurrentLinkedQueue<>();

    private volatile boolean running = true;

    /**
     * Instrumentation for benchmarking
     */
    static final class SocketMetrics {

        private long lastReportNanos;
        private long nextReport;

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

        private long readCalls;
        private long readNanos;
        private long readBytes;
        private long dispatchNanos;
        private long dispatchCalls;
        private long writeCalls;
        private long writeNanos;
        private long writeBytes;

        private long readRequests;
        private long completedRequests;

        private final AtomicLong registrationWakeupCalls = new AtomicLong();
        private final AtomicLong responseWakeupCalls = new AtomicLong();

        private final LongAdder virtualThreadsStarted = new LongAdder();
        private final LongAdder virtualThreadsCompleted = new LongAdder();

        void reset() {
            this.loopCount = 0;
            this.loopNanos = 0;
            this.selectCount = 0;
            this.selectNanos = 0;
            this.selectNowCount = 0;
            this.selectNowNanos = 0;
            this.selectedKeysCount = 0;
            this.selectedKeysNanos = 0;
            this.processedKeysCount = 0;
            this.validKeysCount = 0;
            this.readCalls = 0;
            this.readNanos = 0;
            this.readBytes = 0;
            this.dispatchCalls = 0;
            this.dispatchNanos = 0;
            this.writeCalls = 0;
            this.writeNanos = 0;
            this.writeBytes = 0;
            this.readRequests = 0;
            this.completedRequests = 0;

            this.registrationWakeupCalls.set(0);
            this.responseWakeupCalls.set(0);
        }

        long start() {
            return System.nanoTime();
        }

        void startReporting(long startedAt) {
            this.lastReportNanos = startedAt;
            this.nextReport = startedAt + 1_000_000_000L;
        }

        void report() {
            final long now = System.nanoTime();
            if (now - this.nextReport >= 0) {
                final long elapsed = now - this.lastReportNanos;
                reportStats(elapsed);
                reportReadWriteStats(elapsed);
                reset();
                this.lastReportNanos = System.nanoTime();
                this.nextReport = this.lastReportNanos + 1_000_000_000L;
            }
        }

        void loopFinished(long startedAt) {
            this.loopNanos += System.nanoTime() - startedAt;
            this.loopCount++;
        }

        void selectFinished(long startedAt) {
            this.selectNanos += System.nanoTime() - startedAt;
            this.selectCount++;
        }

        void selectNowFinished(long startedAt) {
            this.selectNowNanos += System.nanoTime() - startedAt;
            this.selectNowCount++;
        }

        void incrementSelectedKeysCount() {
            this.selectedKeysCount++;
        }

        void selectedKeysFinished(long startedAt) {
            this.selectedKeysNanos += System.nanoTime() - startedAt;
        }

        void incrementProcessedKeysCount() {
            this.processedKeysCount++;
        }

        void incrementValidKeysCount() {
            this.validKeysCount++;
        }

        void readFinished(long startedAt) {
            this.readNanos += System.nanoTime() - startedAt;
            this.readCalls++;
        }

        void bytesRead(int count) {
            this.readBytes += count;
        }

        void dispatchFinished(long startedAt) {
            this.dispatchNanos += System.nanoTime() - startedAt;
            this.dispatchCalls++;
        }

        void writeFinished(long startedAt) {
            this.writeNanos += System.nanoTime() - startedAt;
            this.writeCalls++;
        }

        void bytesWritten(int count) {
            this.writeBytes += count;
        }

        void incrementReadRequests() {
            this.readRequests++;
        }

        void incrementCompletedRequests() {
            this.completedRequests++;
        }

        void incrementRegistrationWakeupCalls() {
            this.registrationWakeupCalls.incrementAndGet();
        }

        void incrementResponseWakeupCalls() {
            this.responseWakeupCalls.incrementAndGet();
        }

        void incrementVirtualThreadsStarted() {
            this.virtualThreadsStarted.increment();
        }

        void incrementVirtualThreadsCompleted() {
            this.virtualThreadsCompleted.increment();
        }

        private void reportReadWriteStats(long elapsedNanos) {
            final double seconds = elapsedNanos / 1_000_000_000.0;
            if (elapsedNanos <= 0) return;

            System.out.printf("""
            Thread: %s
            Read:
              calls:       %,d (%.0f/sec)
              total time:  %.2f ms
              avg time:    %.2f ns/call
              requests:    %,d (%.0f/sec)
              bytes:       %,d (%.2f MB/sec)
    
            Dispatch:
              calls:       %,d
              total time:  %.2f ms
              avg time:    %.2f ns/call
    
            Write:
              calls:       %,d (%.0f/sec)
              total time:  %.2f ms
              avg time:    %.2f ns/call
              responses:   %,d (%.0f/sec)
              bytes:       %,d (%.2f MB/sec)
            """,
                Thread.currentThread().getName(),
                this.readCalls, this.readCalls / seconds,
                this.readNanos / 1_000_000.0,
                this.readCalls == 0 ? 0 : (double) this.readNanos / this.readCalls,
                this.readRequests, this.readRequests / seconds,
                this.readBytes, this.readBytes / seconds / 1_000_000.0,

                this.dispatchCalls,
                this.dispatchNanos / 1_000_000.0,
                this.dispatchCalls == 0 ? 0 : (double) this.dispatchNanos / this.dispatchCalls,

                this.writeCalls, this.writeCalls / seconds,
                this.writeNanos / 1_000_000.0,
                this.writeCalls == 0 ? 0 : (double) this.writeNanos / this.writeCalls,
                this.completedRequests, this.completedRequests / seconds,
                this.writeBytes, this.writeBytes / seconds / 1_000_000.0
            );
        }

        private void reportStats(long elapsedNanos) {
            final double seconds = elapsedNanos / 1_000_000_000.0;
            if (elapsedNanos <= 0) return;

            final long requests = this.completedRequests;
            final long loops = this.loopCount;
            final long totalWakeups = this.registrationWakeupCalls.get() + this.responseWakeupCalls.get();

            final double avgLoopNanos =
                loops == 0 ? 0 : (double) this.loopNanos / loops;

            final double avgSelectNanos =
                this.selectCount == 0 ? 0 : (double) this.selectNanos / this.selectCount;

            final double avgSelectNowNanos =
                this.selectNowCount == 0 ? 0 : (double) this.selectNowNanos / this.selectNowCount;

            final double avgRequestsPerLoop =
                loops == 0 ? 0 : (double) requests / loops;

            final double wakeupsPerRequest =
                requests == 0 ? 0 : (double) this.responseWakeupCalls.get() / requests;

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

            final long activeVTs = this.virtualThreadsStarted.sum() - this.virtualThreadsCompleted.sum();

            log.info(() -> String.format(
                """ 
                    Thread: %s
                    SelectorStats:
                      loops/sec:                            %.0f
                      avg loop:                             %.1f ns
                      select calls/sec:                     %.0f
                      avg select:                           %.1f ns
                      selectNow calls/sec:                  %.0f
                      avg selectNow:                        %.1f ns
                      selected keys/sec:                    %.0f
                      processed keys/sec:                   %.0f
                      valid keys/sec:                       %.0f
                      selected keys/loop:                   %.3f
                      processed keys/loop:                  %.3f
                      valid keys/loop:                      %.3f
                      selected processing:                  %.2f%%
                      select time:                          %.2f%%
                      other  time:                          %.2f%%
                      (completed) requests/sec:             %.0f
                      (completed) requests/loop:            %.3f
                      response wakeups/sec:                 %.0f
                      registration wakeups/sec:             %.0f
                      wakeup calls/(completed) request:     %.3f
                      total wakeup calls/sec:               %.0f
                      virtual threads (active):             %d
                      virtual threads (started/sec):        %.0f
                      virtual threads (completed/sec):      %.0f
                    """,
                Thread.currentThread().getName(),
                this.loopCount / seconds,
                avgLoopNanos,
                this.selectCount / seconds,
                avgSelectNanos,
                this.selectNowCount / seconds,
                avgSelectNowNanos,
                this.selectedKeysCount / seconds,
                this.processedKeysCount / seconds,
                this.validKeysCount / seconds,
                avgSelectedKeysPerLoop,
                avgProcessedKeysPerLoop,
                avgValidKeysPerLoop,
                selectedProcessingPercent,
                selectPercent,
                otherProcessingPercent,
                this.completedRequests / seconds,
                avgRequestsPerLoop,
                this.responseWakeupCalls.get() / seconds,
                this.registrationWakeupCalls.get() / seconds,
                wakeupsPerRequest,
                totalWakeups / seconds,
                activeVTs,
                this.virtualThreadsStarted.longValue() / seconds,
                this.virtualThreadsCompleted.longValue() / seconds
            ));
        }

    }

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
        if (this.metrics != null) {
            this.metrics.incrementRegistrationWakeupCalls();
        }
        this.selector.wakeup();
    }

    @Override
    public void run() {
        try {
            long now = System.nanoTime();
            long nextTimeoutScan = now + TIMEOUT_SCAN_INTERVAL_NANOS;
            if (this.metrics != null) {
                this.metrics.startReporting(now);
            }
            while (this.running && !Thread.currentThread().isInterrupted()) {
                final long loopStart = this.metrics != null ? this.metrics.start() : System.nanoTime();
                now = loopStart;
                registerPendingConnections(now);
                processPendingReads(now);

                final long timeout = computeSelectTimeout(now, nextTimeoutScan);
                final int ready;

                final long start = this.metrics != null ? this.metrics.start() : 0L;
                if (timeout == 0) {
                    ready = this.selector.selectNow();
                    if (this.metrics != null) {
                        this.metrics.selectNowFinished(start);
                    }
                } else {
                    ready = this.selector.select(timeout);
                    if (this.metrics != null) {
                        this.metrics.selectFinished(start);
                    }
                }

                now = System.nanoTime();
                registerPendingConnections(now);
                if (ready > 0) {
                    if (this.metrics != null) {
                        this.metrics.incrementSelectedKeysCount();
                    }
                    processSelectedKeys(now);
                }
                processWritableSockets();
                if (now - nextTimeoutScan >= 0) {
                    checkSocketReadTimeouts(now);
                    nextTimeoutScan = now + TIMEOUT_SCAN_INTERVAL_NANOS;
                }

                if (this.metrics != null) {
                    this.metrics.loopFinished(loopStart);
                    this.metrics.report();
                }
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
            final SocketConnection conn = new SocketConnection(key, socket, this.protocolHandler, this.readTimeoutWheel, now);
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
        final long start = this.metrics != null ? this.metrics.start() : 0L;
        try {
            final Iterator<SelectionKey> iterator = this.selector.selectedKeys().iterator();
            SelectionKey key;
            SocketConnection conn;
            while (iterator.hasNext()) {
                key = iterator.next();
                iterator.remove();
                if (this.metrics != null) {
                    this.metrics.incrementProcessedKeysCount();
                }

                if (!key.isValid()) {
                    continue;
                }
                if (this.metrics != null) {
                    this.metrics.incrementValidKeysCount();
                }

                conn = (SocketConnection) key.attachment();
                if (conn == null) {
                    key.cancel();
                    continue;
                }

                if (key.isReadable()) {
                    processRead(conn, now);
                    if (!key.isValid()) {
                        continue;
                    }
                }
                if (key.isWritable()) {
                    processWrite(conn, now);
                }
            }
        } finally {
            if (this.metrics != null) {
                this.metrics.selectedKeysFinished(start);
            }
        }
    }

    private void processRead(SocketConnection conn, long now) {
        try {
            int requests = 0;
            while (requests < MAX_REQUESTS_PER_TURN) {
                long start = this.metrics != null ? this.metrics.start() : 0L;
                final boolean ready;
                try {
                    ready = conn.read(this.transportBuffer, now);
                } finally {
                    if (this.metrics != null) {
                        this.metrics.readFinished(start);
                        this.metrics.bytesRead(conn.readBytesThisCall);
                    }
                }
                if (!ready) {
                    break;
                }
                if (this.metrics != null) {
                    this.metrics.incrementReadRequests();
                    start = this.metrics.start();
                }
                dispatch(conn);
                requests++;
                if (this.metrics != null) {
                    this.metrics.dispatchFinished(start);
                }
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
                    conn.key.interestOps(conn.key.interestOps() & ~SelectionKey.OP_WRITE); // disable write
                    return;
                }
            }
            final var reader = conn.response.writer().reader();
            try {
                final long start = this.metrics != null ? this.metrics.start() : 0L;
                try {
                    conn.write(reader, now);
                } finally {
                    if (this.metrics != null) {
                        this.metrics.writeFinished(start);
                        this.metrics.bytesWritten(conn.writeBytesThisCall);
                    }
                }
                if (!reader.hasRemaining()) {
                    final long sequence = conn.response.sequence();
                    reader.close(); // release blocks
                    conn.response = null;
                    conn.responseBuffer.update(sequence, ResponseBuffer.SlotState.FREE);
                    responses++;
                    if (this.metrics != null) {
                        this.metrics.incrementCompletedRequests();
                    }
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
                    if (this.metrics != null) {
                        this.metrics.incrementResponseWakeupCalls();
                    }
                    this.selector.wakeup();
                }
            }
        }
    }

    private void checkSocketReadTimeouts(long now) {
        this.readTimeoutWheel.expire(now, conn -> {
            conn.close();
            removeConnection(conn);
        });
    }

    private long computeSelectTimeout(long now, long nextTimeoutScan) {
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
        this.errors.clear();
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
