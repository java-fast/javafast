package io.javafast.transport;

import java.lang.foreign.MemorySegment;

public class FixedSource implements TransportSource {
    private static final byte FILL_VALUE = 1;

    private final int fillSize;
    private int remaining;

    public FixedSource(int fillSize) {
        if (fillSize < 0) {
            throw new IllegalArgumentException("fillSize must be >= 0");
        }
        this.fillSize = fillSize;
        this.remaining = fillSize;
    }

    public void reset() {
        this.remaining = this.fillSize;
    }

    @Override
    public int read(MemorySegment destination) {
        if (this.remaining == 0) {
            return -1;
        }
        final int writeSize = (int) Math.min(destination.byteSize(), this.remaining);
        destination.asSlice(0, writeSize).fill(FILL_VALUE);
        this.remaining -= writeSize;
        return writeSize;
    }
}
