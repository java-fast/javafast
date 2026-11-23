package io.javafast.transport.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Default Instrumentation for benchmarking
 */
class DefaultSelectorMetrics implements SelectorMetrics {

    private static final Logger log = Logger.getLogger(DefaultSelectorMetrics.class.getName());

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
    private final LongAdder virtualTasksCompleted = new LongAdder();

    @Override
    public void reset() {
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

        this.virtualThreadsStarted.reset();
        this.virtualTasksCompleted.reset();
    }

    @Override
    public void startReporting(long startedAt) {
        this.lastReportNanos = startedAt;
        this.nextReport = startedAt + 1_000_000_000L;
    }

    @Override
    public void report() {
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

    @Override
    public void loopFinished(long startedAt) {
        this.loopNanos += System.nanoTime() - startedAt;
        this.loopCount++;
    }

    @Override
    public void selectFinished(long startedAt) {
        this.selectNanos += System.nanoTime() - startedAt;
        this.selectCount++;
    }

    @Override
    public void selectNowFinished(long startedAt) {
        this.selectNowNanos += System.nanoTime() - startedAt;
        this.selectNowCount++;
    }

    @Override
    public void incrementSelectedKeysCount(int selected) {
        this.selectedKeysCount += selected;
    }

    @Override
    public void selectedKeysFinished(long startedAt) {
        this.selectedKeysNanos += System.nanoTime() - startedAt;
    }

    @Override
    public void incrementProcessedKeysCount() {
        this.processedKeysCount++;
    }

    @Override
    public void incrementValidKeysCount() {
        this.validKeysCount++;
    }

    @Override
    public void readFinished(long startedAt) {
        this.readNanos += System.nanoTime() - startedAt;
        this.readCalls++;
    }

    @Override
    public void bytesRead(int count) {
        this.readBytes += count;
    }

    @Override
    public void dispatchFinished(long startedAt) {
        this.dispatchNanos += System.nanoTime() - startedAt;
        this.dispatchCalls++;
    }

    @Override
    public void writeFinished(long startedAt) {
        this.writeNanos += System.nanoTime() - startedAt;
        this.writeCalls++;
    }

    @Override
    public void bytesWritten(int count) {
        this.writeBytes += count;
    }

    @Override
    public void incrementReadRequests() {
        this.readRequests++;
    }

    @Override
    public void incrementCompletedRequests() {
        this.completedRequests++;
    }

    @Override
    public void incrementRegistrationWakeupCalls() {
        this.registrationWakeupCalls.incrementAndGet();
    }

    @Override
    public void incrementResponseWakeupCalls() {
        this.responseWakeupCalls.incrementAndGet();
    }

    @Override
    public void incrementVirtualThreadsStarted() {
        this.virtualThreadsStarted.increment();
    }

    @Override
    public void incrementVirtualTasksCompleted() {
        this.virtualTasksCompleted.increment();
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

        final long activeVTs = this.virtualThreadsStarted.sum() - this.virtualTasksCompleted.sum();

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
            this.virtualTasksCompleted.longValue() / seconds
        ));
    }

}
