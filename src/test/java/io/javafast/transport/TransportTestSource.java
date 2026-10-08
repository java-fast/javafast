package io.javafast.transport;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public interface TransportTestSource extends ReadableByteChannel {

    @Override
    default boolean isOpen() {
        return true;
    }

    @Override
    default void close() {
        // no-op
    }

    /**
     * Fills destination
     *
     * @param destination target ByteBuffer
     * @return POSITIVE for number of bytes written, -1 for source finished
     */
    @Override
    int read(ByteBuffer destination);

    default TransportBuffer.Writer copyTo(TransportBuffer buffer) {
        try {
            while (true) {
                final TransportBuffer.Writer writer = buffer.writer();
                if (writer == null) {
                    Thread.sleep(1); // if buffer overflows, then wait and retry
                    continue;
                }
                final long time = System.currentTimeMillis();
                while (System.currentTimeMillis() - time < 100) { // read timeout 100 milliseconds
                    try {
                        // -1 semantics cause an infinite loop here
                        // a channel can return always 0 if the buffer is exhausted
                        if (writer.read(this) < 0) break;
                    } catch (BufferOverflowException _) {
                        Thread.sleep(1); // if buffer overflows, then wait and retry
                    }
                }
                return writer;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    default int readAll(TransportBuffer buffer, TransportBuffer.Block head) {
        int total = 0;
        int read;
        var current = head;
        var payload = current.byteBuffer();
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
                payload = next.byteBuffer();
            }
        }
        return total;
    }

}
