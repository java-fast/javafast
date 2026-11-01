package io.javafast.transport;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

final class SocketConnection {

    private static final Logger log = Logger.getLogger(SocketConnection.class.getName());

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

    /**
     * Instrumentation for benchmarking
     */
    private long multiRequestReads;
    private long discardedBytes;


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
            final int written;
            try {
                written = this.pendingBytes > 0
                    ? this.pendingBytes
                    : this.writer.read(this.channel);
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

            bytes += written;
            reads++;
            this.lastReadNanos = now;
            // FIXME a socket read can have multiple HTTP requests!
            // In this case, we should know the first request boundary (where it finishes)
            // Then, discard the other requests' bytes and publish the first one
            // Because a TransportBuffer.Block must always have a single HTTP request!
            this.protocolHandler.onBytes(this.writer.reader(), written);
            final int requestLength = this.protocolHandler.requestLength();
            if (this.protocolHandler.payloadComplete()) {
                TransportBuffer.Writer fork = null;
                this.pendingBytes = this.writer.totalWritten() - requestLength;
                if (this.pendingBytes > 0) {
                    this.multiRequestReads++;
                    this.discardedBytes += this.pendingBytes;
                    log.info(String.format("Discarded %d bytes from multi-request read: %d", this.discardedBytes, this.multiRequestReads));
                    fork = this.writer.fork(requestLength);
                }
                if (publish()) {
                    this.writer = fork;
                    return true;
                }
                return false; // publish failed
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
