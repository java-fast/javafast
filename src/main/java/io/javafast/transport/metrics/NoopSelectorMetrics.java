package io.javafast.transport.metrics;

class NoopSelectorMetrics implements SelectorMetrics {

    @Override
    public long start() {
        return 0L;
    }

    @Override
    public void reset() {

    }

    @Override
    public void startReporting(long startedAt) {

    }

    @Override
    public void report() {

    }

    @Override
    public void loopFinished(long startedAt) {

    }

    @Override
    public void selectFinished(long startedAt) {

    }

    @Override
    public void selectNowFinished(long startedAt) {

    }

    @Override
    public void incrementSelectedKeysCount(int selected) {

    }

    @Override
    public void selectedKeysFinished(long startedAt) {

    }

    @Override
    public void incrementProcessedKeysCount() {

    }

    @Override
    public void incrementValidKeysCount() {

    }

    @Override
    public void readFinished(long startedAt) {

    }

    @Override
    public void bytesRead(int count) {

    }

    @Override
    public void dispatchFinished(long startedAt) {

    }

    @Override
    public void writeFinished(long startedAt) {

    }

    @Override
    public void bytesWritten(int count) {

    }

    @Override
    public void incrementReadRequests() {

    }

    @Override
    public void incrementCompletedRequests() {

    }

    @Override
    public void incrementRegistrationWakeupCalls() {

    }

    @Override
    public void incrementResponseWakeupCalls() {

    }

    @Override
    public void incrementVirtualThreadsStarted() {

    }

    @Override
    public void incrementVirtualTasksCompleted() {

    }

}
