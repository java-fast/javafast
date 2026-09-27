package io.javafast.transport;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public interface TransportTestSource {

    /**
     * Fills destination
     *
     * @param destination target ByteBuffer
     * @return POSITIVE for number of bytes written, -1 for source finished
     */
    default int read(ByteBuffer destination) {
        return 0;
    }

    default int readAll(TransportBuffer buffer, TransportBuffer.Block head) {
        int total = 0;
        int read;
        var current = head;
        var payload = current.payload();
        while ((read = read(payload)) > 0) {
            total += read;
            if (!payload.hasRemaining()) {
                final var next = buffer.acquire();
                if (next == null) {
                    head.release();
                    throw new BufferOverflowException();
                }
                current.link(next);
                current = next;
                payload = next.payload();
            }
        }
        return total;
    }

}
