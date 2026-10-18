package io.javafast.internal;

import java.util.concurrent.atomic.AtomicInteger;

public class RequestContextPool {

    public static RequestContextPool create(int size) {
        return new RequestContextPool(size);
    }

    private final RequestContext[] ctxs;
    private final AtomicInteger cursor = new AtomicInteger(0);

    private RequestContextPool(int size) {
        this.ctxs = new RequestContext[size];
        for (int i = 0; i < size; i++) {
            this.ctxs[i] = new RequestContext();
        }
    }

    RequestContext get() {
        // TODO occupy and release
        return this.ctxs[this.cursor.getAndIncrement() % this.ctxs.length];
    }

    void release(RequestContext ctx) {
        // TODO release
    }
}
