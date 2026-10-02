package io.javafast.transport;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.channels.SocketChannel;

final class SocketConnection {

    private static final int MAX_READS_PER_TURN = 16;
    private static final int MAX_READ_BYTES_PER_TURN = 64 * 1024;

    final SocketChannel channel;

    // Request state
    TransportBuffer.Writer writer;
    TransportBuffer.Reader published;

    long lastReadNanos;

    ProtocolHandler protocolHandler;

    SocketConnection(SocketChannel channel, ProtocolHandler protocolHandler, long now) {
        this.channel = channel;
        this.protocolHandler = protocolHandler;
        this.lastReadNanos = now;
    }

    boolean read(TransportBuffer buffer, long now) throws IOException {
        if (this.writer == null) {
            this.writer = buffer.writer();
            if (this.writer == null) {
                return false; // TransportBuffer exhausted. Don't block selector.
            }
        }
        int reads = 0;
        int bytes = 0;
        while (reads < MAX_READS_PER_TURN && bytes < MAX_READ_BYTES_PER_TURN) {
            final int written;
            try {
                written = this.writer.read(this.channel);
            } catch (BufferOverflowException e) {
                discard();
                throw new ConnectionClosedException(e);
            }
            if (written == -1) {
                discard();
                throw new ConnectionClosedException();
            }
            if (written == 0) {
                break;
            }

            bytes += written;
            reads++;
            this.lastReadNanos = now;
            this.protocolHandler.onBytes(this.writer.reader(), written);
            if (this.protocolHandler.payloadComplete()) {
                publish();
                return true;
            }
        }

        return false;
    }

    private void publish() {
        this.published = this.writer.reader();
        this.published.reset();
        this.protocolHandler.reset();
        this.writer = null;
    }

    private void discard() {
        if (this.writer != null) {
            this.writer.release();
            this.writer = null;
            this.protocolHandler.reset();
        }
    }

    void close() {
        try {
            this.channel.close();
        } catch (IOException _) {
            // no-op
        }
        discard();
    }
}
