package io.javafast.transport;

import io.javafast.util.Utils;
import io.javafast.util.function.ThrowingSupplier;

import java.nio.channels.SocketChannel;

final class SocketSelectorGroup {

    private int nextSelector;
    private final SocketSelector[] selectors;

    SocketSelectorGroup(int count, ThrowingSupplier<SocketSelector> supplier) {
        if (Utils.isNotPowerOfTwo(count)) {
            throw new IllegalArgumentException("count must be power of two");
        }
        this.selectors = new SocketSelector[count];
        for (int i = 0; i < count; i++) {
            try {
                this.selectors[i] = supplier.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    int count() {
        return this.selectors.length;
    }

    SocketSelector get(int index) {
        return this.selectors[index];
    }

    void register(SocketChannel channel) {
        this.selectors[this.nextSelector++ & (this.selectors.length - 1)].register(channel);
    }

    public void shutdown() {
        for (SocketSelector selector : this.selectors) {
            selector.shutdown();
        }
    }
}
