package io.javafast.transport;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class FixedSource implements TransportTestSource {

    private static final byte FILL_VALUE = 1;

    private final ByteBuffer fillBuffer;

    private final int fillSize;
    private int remaining;

    public FixedSource(int fillSize) {
        if (fillSize < 0) {
            throw new IllegalArgumentException("fillSize must be >= 0");
        }
        this.fillSize = fillSize;
        this.remaining = fillSize;
        this.fillBuffer = ByteBuffer.allocate(fillSize);
        Arrays.fill(this.fillBuffer.array(), FILL_VALUE);
    }

    public void reset() {
        this.remaining = this.fillSize;
    }

    @Override
    public int read(ByteBuffer destination) {
        if (this.remaining == 0) {
            return -1;
        }
        final int writeSize = Math.min(destination.remaining(), this.remaining);
        destination.put(this.fillBuffer.slice(0, writeSize));
        this.remaining -= writeSize;
        return writeSize;
    }

}
