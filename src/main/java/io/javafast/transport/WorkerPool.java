package io.javafast.transport;

import io.javafast.transport.metrics.SelectorMetrics;
import io.javafast.util.Utils;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

final class WorkerPool {

    private static final Logger log = Logger.getLogger(WorkerPool.class.getName());

    private final SelectorMetrics metrics;

    private final class Worker implements Runnable {

        private final AtomicReference<Runnable> task = new AtomicReference<>();
        private final Thread thread;

        private volatile boolean mightBeDead = false;

        private Worker() {
            this.thread = Thread.ofVirtual().unstarted(this);
            this.thread.start();
            metrics.incrementVirtualThreadsStarted();
        }

        boolean submit(Runnable command) {
            if (this.mightBeDead) {
                return false;
            }
            if (!this.task.compareAndSet(null, command)) {
                return false;
            }
            LockSupport.unpark(this.thread);
            return true;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                Runnable command;
                while ((command = this.task.getAndSet(null)) == null) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    LockSupport.park();
                }
                try {
                    command.run();
                    metrics.incrementVirtualTasksCompleted();
                } catch (Exception e) {
                    log.log(Level.WARNING, "Exception in worker task", e);
                    // for extra safety in case the task doesn't handle exceptions,
                    // so we let the worker live
                } catch (Error e) {
                    log.log(Level.SEVERE, "Worker task failed unexpectedly", e);
                    this.mightBeDead = true;
                }
            }
        }
    }

    private final Worker[] workers;
    private final int mask;
    private int nextWorker;

    WorkerPool(int size, SelectorMetrics metrics) {
        if (Utils.isNotPowerOfTwo(size)) {
            throw new IllegalArgumentException("size must be a power of two");
        }
        this.metrics = metrics;
        this.mask = size - 1;
        this.workers = new Worker[size];
        for (int i = 0; i < size; i++) {
            this.workers[i] = new Worker();
        }
    }

    void submit(Runnable task) {
        final int start = this.nextWorker++;
        for (int i = 0; i < this.workers.length; i++) {
            final int index = (start + i) & this.mask;
            if (this.workers[index].submit(task)) {
                return;
            }
        }
        Thread.ofVirtual().start(task); // fallback, all workers are full, then we create a new VT
        this.metrics.incrementVirtualThreadsStarted();
    }
}
