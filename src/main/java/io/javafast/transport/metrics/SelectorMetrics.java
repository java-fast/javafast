package io.javafast.transport.metrics;

public interface SelectorMetrics {

    boolean INSTRUMENTATION_ENABLED = Boolean.getBoolean("javafast.instrumentation");

    static SelectorMetrics create() {
        return INSTRUMENTATION_ENABLED ? new DefaultSelectorMetrics() : new NoopSelectorMetrics();
    }

    default long start() {
        return System.nanoTime();
    }

    void reset();

    void startReporting(long startedAt);

    void report();

    void loopFinished(long startedAt);

    void selectFinished(long startedAt);

    void selectNowFinished(long startedAt);

    void incrementSelectedKeysCount(int selected);

    void selectedKeysFinished(long startedAt);

    void incrementProcessedKeysCount();

    void incrementValidKeysCount();

    void readFinished(long startedAt);

    void bytesRead(int count);

    void dispatchFinished(long startedAt);

    void writeFinished(long startedAt);

    void bytesWritten(int count);

    void incrementReadRequests();

    void incrementCompletedRequests();

    void incrementRegistrationWakeupCalls();

    void incrementResponseWakeupCalls();

    void incrementVirtualThreadsStarted();

    void incrementVirtualTasksCompleted();

}
