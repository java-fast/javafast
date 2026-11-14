package io.javafast.transport;

import io.javafast.util.Utils;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

final class DispatcherPool {

    private final static class Worker implements Runnable {

        private final AtomicReference<Runnable> task = new AtomicReference<>();
        private final Thread thread;

        private Worker() {
            this.thread = Thread.ofVirtual().unstarted(this);
            this.thread.start();
        }

        boolean submit(Runnable command) {
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
                    LockSupport.park();
                }
                try {
                    command.run();
                } catch (Exception e) {
                    // let the worker live
                    // TODO handle exception cases
                }
            }
        }
    }

    private final Worker[] workers;
    private int nextWorker;

    DispatcherPool(int size) {
        if (Utils.isNotPowerOfTwo(size)) {
            throw new IllegalArgumentException("size must be a power of two");
        }
        this.workers = new Worker[size];
        for (int i = 0; i < size; i++) {
            this.workers[i] = new Worker();
        }
    }

    void submit(Runnable task) {
        final int start = this.nextWorker++ & (this.workers.length - 1);
        for (int i = 0; i < this.workers.length; i++) {
            final int index = (start + i) % this.workers.length;
            if (this.workers[index].submit(task)) {
                return;
            }
        }
        Thread.ofVirtual().start(task); // fallback, all workers are full, then we create a new VT
    }
}
