package io.javafast.internal;

import java.util.concurrent.atomic.AtomicInteger;

public class RequestContextPool {

    public static RequestContextPool create(int size) {
        return new RequestContextPool(size);
    }

    private static boolean isNotPowerOfTwo(int n) {
        return n == 0 || (n & n - 1) != 0;
    }

    private final int sizeMask;
    private final RequestContext[] ctxs;

    private final AtomicInteger cursor = new AtomicInteger(0);

    private RequestContextPool(int size) {
        if (size < 0 || isNotPowerOfTwo(size)) {
            throw new IllegalArgumentException("size must be > 0 and a power of two");
        }
        this.sizeMask = size - 1;
        this.ctxs = new RequestContext[size];
        for (int i = 0; i < size; i++) {
            this.ctxs[i] = new RequestContext();
        }
    }

    RequestContext get() {
        final int startIndex = this.cursor.getAndIncrement() & this.sizeMask;
        int index = startIndex;
        do {
            final RequestContext ctx = this.ctxs[index];
            if (ctx.occupied.compareAndSet(false, true)) {
                return ctx;
            }
            index = (index + 1) & this.sizeMask;
        } while (index != startIndex);
        // no free context found, create a new one to keep processing at the cost of an allocation
        // no need to make occupied=true since this will be thrown away
        return new RequestContext();
    }

    void release(RequestContext ctx) {
        ctx.occupied.set(false);
    }
}
