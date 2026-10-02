package io.javafast.internal;

public class RequestContextPool {

    public static RequestContextPool create(int size) {
        return new RequestContextPool(size);
    }

    final RequestContext[] ctxs;
    int cursor = -1;

    private RequestContextPool(int size) {
        this.ctxs = new RequestContext[size];
        for (int i = 0; i < size; i++) {
            this.ctxs[i] = new RequestContext();
        }
    }

    RequestContext get() {
        // TODO occupy and release
        return this.ctxs[this.cursor++ % this.ctxs.length];
    }

    void release(RequestContext ctx) {
        // TODO release
    }
}
