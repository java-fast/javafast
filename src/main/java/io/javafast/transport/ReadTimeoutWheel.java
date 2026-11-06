
package io.javafast.transport;

import io.javafast.util.Utils;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A wheel of timeouts for tracking socket-read timeouts.
 */
final class ReadTimeoutWheel {

    private final SocketConnection[] buckets;
    private final int wheelSize;
    private final long tickNanos;
    private final long originNanos;

    private long currentTick;

    ReadTimeoutWheel(int wheelSize, long tickNanos, long now) {
        if (wheelSize <= 0) {
            throw new IllegalArgumentException("wheelSize must be positive");
        }
        if (Utils.isNotPowerOfTwo(wheelSize)) {
            throw new IllegalArgumentException("wheelSize must be power of two");
        }
        if (tickNanos <= 0) {
            throw new IllegalArgumentException("tickNanos must be positive");
        }

        this.wheelSize = wheelSize;
        this.tickNanos = tickNanos;
        this.buckets = new SocketConnection[wheelSize];
        this.originNanos = now;
        this.currentTick = 0;
    }

    void add(SocketConnection conn) {
        Objects.requireNonNull(conn);
        if (conn.timeoutBucket >= 0) {
            throw new IllegalStateException("Connection already registered");
        }
        insert(conn);
    }

    void update(SocketConnection conn) {
        Objects.requireNonNull(conn);
        if (conn.timeoutBucket >= 0) {
            unlink(conn);
        }
        insert(conn);
    }

    void remove(SocketConnection conn) {
        Objects.requireNonNull(conn);
        if (conn.timeoutBucket >= 0) {
            unlink(conn);
        }
    }

    /**
     * Processes expired connections.
     *
     * @param now current System.nanoTime()
     */
    public void expire(long now, Consumer<SocketConnection> onExpire) {
        final long targetTick = elapsedTicks(now);
        while (this.currentTick <= targetTick) {
            final int bucketIndex = (int) (this.currentTick & this.wheelSize - 1);
            SocketConnection conn = this.buckets[bucketIndex];
            while (conn != null) {
                final SocketConnection next = conn.timeoutNext;
                if (now - conn.readDeadlineNanos >= 0) {
                    unlink(conn);
                    onExpire.accept(conn);
                }
                conn = next;
            }
            this.currentTick++;
        }

    }

    private void insert(SocketConnection conn) {
        final long deadlineTick = deadlineTick(conn.readDeadlineNanos);
        final long targetTick = Math.max(this.currentTick, deadlineTick);
        final int bucketIndex = (int) (targetTick & this.wheelSize - 1);
        conn.timeoutBucket = bucketIndex;
        conn.timeoutNext = this.buckets[bucketIndex];
        conn.timeoutPrev = null;
        if (conn.timeoutNext != null) {
            conn.timeoutNext.timeoutPrev = conn;
        }
        this.buckets[bucketIndex] = conn;
    }

    private void unlink(SocketConnection conn) {
        final int bucketIndex = conn.timeoutBucket;
        if (conn.timeoutPrev == null) {
            this.buckets[bucketIndex] = conn.timeoutNext;
        } else {
            conn.timeoutPrev.timeoutNext = conn.timeoutNext;
        }
        if (conn.timeoutNext != null) {
            conn.timeoutNext.timeoutPrev = conn.timeoutPrev;
        }
        conn.timeoutNext = null;
        conn.timeoutPrev = null;
        conn.timeoutBucket = -1;
    }

    private long elapsedTicks(long now) {
        return Math.max(0, (now - this.originNanos) / this.tickNanos);
    }

    private long deadlineTick(long deadlineNanos) {
        final long elapsed = deadlineNanos - this.originNanos;
        if (elapsed <= 0) {
            return 0;
        }
        return (elapsed + this.tickNanos - 1) / this.tickNanos;
    }
}
