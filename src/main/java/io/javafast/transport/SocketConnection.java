package io.javafast.transport;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

final class SocketConnection {

    private static final int MAX_READS_PER_TURN = 16;
    private static final int MAX_READ_BYTES_PER_TURN = 64 * 1024;

    private final SelectionKey key;
    private final SocketChannel channel;
    private final ProtocolHandler protocolHandler;
    final ResponseBuffer responseBuffer = ResponseBuffer.create(Integer.getInteger("javafast.socket.response.buffer.size", 16));
    long lastReadNanos; // socket read time
    long lastWriteNanos; // socket write time

    TransportBuffer.Writer writer;
    TransportBuffer.Writer published;
    long publishedSequence;

    ResponseBuffer.ResponseSlot response; // current response

    SocketConnection(SelectionKey key, SocketChannel channel, ProtocolHandler protocolHandler, long now) {
        this.key = key;
        this.channel = channel;
        this.protocolHandler = protocolHandler;
        this.lastReadNanos = now;
    }

    SelectionKey key() {
        return this.key;
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
            // FIXME a socket read can have multiple HTTP requests!
            // In this case, we should know the first request boundary (where it finishes)
            // Then, discard the other requests' bytes and publish the first one
            // Because a TransportBuffer.Block must always have a single HTTP request!
            this.protocolHandler.onBytes(this.writer.reader(), written);
            if (this.protocolHandler.payloadComplete()) {
                return publish();
            }
        }

        return false;
    }

    private boolean publish() {
        final TransportBuffer.Reader reader = this.writer.reader();
        final long sequence = this.responseBuffer.offer(reader);
        if (sequence != -1) {
            reader.reset();
            this.publishedSequence = sequence;
            this.published = this.writer;
            this.protocolHandler.reset();
            this.writer = null;
            return true;
        }
        // TODO disable OP_READ?
        return false; // response buffer is full, backpressue is done by TCP socket buffers
    }

    private void discard() {
        if (this.writer != null) {
            this.writer.release();
            this.writer = null;
            this.protocolHandler.reset();
        }
    }

    void write(TransportBuffer.Reader reader, long now) throws IOException {
        if (reader.write(this.channel) > 0) {
            this.lastWriteNanos = now;
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
