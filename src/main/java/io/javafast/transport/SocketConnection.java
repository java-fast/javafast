package io.javafast.transport;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

final class SocketConnection {

    private static final int MAX_READS_PER_TURN = 16;
    private static final int MAX_READ_BYTES_PER_TURN = 64 * 1024;

    private boolean closed;

    private final SocketChannel channel;
    private final ProtocolHandler protocolHandler;
    final SelectionKey key;
    final ResponseBuffer responseBuffer = ResponseBuffer.create(Integer.getInteger("javafast.socket.response.buffer.size", 16));
    long lastReadNanos; // socket read time
    long lastWriteNanos; // socket write time

    TransportBuffer.Writer writer;
    TransportBuffer.Writer published;
    long publishedSequence;

    ResponseBuffer.ResponseSlot response; // current response

    SocketConnection writableNext;
    boolean writableQueued;

    SocketConnection(SelectionKey key, SocketChannel channel, ProtocolHandler protocolHandler, long now) {
        this.key = key;
        this.channel = channel;
        this.protocolHandler = protocolHandler;
        this.lastReadNanos = now;
    }

    private int pendingBytes;
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
            if (this.pendingBytes == 0) { // read from socket
                final int written;
                try {
                    written = this.writer.read(this.channel);
                } catch (BufferOverflowException e) {
                    discard();
                    throw ConnectionClosedException.get(e);
                }
                if (written == -1) {
                    discard();
                    throw ConnectionClosedException.get();
                }
                if (written == 0) {
                    break;
                }
                this.pendingBytes = written;
                this.lastReadNanos = now;
            }

            final int available = this.pendingBytes;
            this.pendingBytes = 0;
            bytes += available;
            reads++;

            this.protocolHandler.onBytes(this.writer.reader(), available);
            final int requestLength = this.protocolHandler.requestLength();
            if (this.protocolHandler.payloadComplete()) {
                final int remaining = this.writer.totalWritten() - requestLength;
                TransportBuffer.Writer fork = null;
                if (remaining > 0) {
                    fork = this.writer.fork(requestLength);
                }
                if (publish()) {
                    this.writer = fork;
                    this.pendingBytes = remaining;
                    return true;
                }
                if (fork != null) {
                    fork.release(); // publish failed, release the fork if any
                }
                return false;
            }
        }

        return false;
    }

    private boolean publish() {
        final TransportBuffer.Reader reader = this.writer.reader();
        final long sequence = this.responseBuffer.offer(reader);
        if (sequence != -1) {
            final int limit = reader.position();
            reader.limit(limit); // limit the reader to discard multiple requests written if any
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
        if (this.closed) {
            return;
        }
        this.closed = true;
        if (this.response != null) {
            this.response.reader().close();
            this.response = null;
        }
        try {
            this.channel.close();
        } catch (IOException _) {
            // best effort shutdown
        }
        discard();
    }
}
