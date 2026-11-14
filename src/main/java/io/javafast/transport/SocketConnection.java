package io.javafast.transport;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

final class SocketConnection {

    private static final long READ_IDLE_TIMEOUT_NANOS = Long.getLong("javafast.read.timeout.nanos", TimeUnit.SECONDS.toNanos(10)); // default 10 seconds

    private static final int MAX_READS_PER_TURN = 16;
    private static final int MAX_READ_BYTES_PER_TURN = 64 * 1024;

    int index = -1; // to identify SocketConnection for swap-remove in SocketSelector
    private boolean closed;

    private final SocketChannel channel;
    private final ProtocolHandler protocolHandler;
    private final ReadTimeoutWheel readTimeoutWheel;
    final SelectionKey key;
    final ResponseBuffer responseBuffer = ResponseBuffer.create(Integer.getInteger("javafast.socket.response.buffer.size", 32));

    private TransportBuffer.Writer writer; // current writer

    // Timing Wheel
    SocketConnection timeoutNext;
    SocketConnection timeoutPrev;
    int timeoutBucket = -1;
    long readDeadlineNanos; // next bucket
    long lastReadNanos; // socket read time
    long lastWriteNanos; // socket write time

    // published writer
    TransportBuffer.Writer published;
    long publishedSequence;

    ResponseBuffer.ResponseSlot response; // current response

    // read pending queue
    SocketConnection readPendingNext;
    boolean readPendingQueued;
    // writable queue
    SocketConnection writableNext;
    boolean writableQueued;

    // instrumentation fields
    int readBytesThisCall;

    SocketConnection(SelectionKey key, SocketChannel channel, ProtocolHandler protocolHandler, ReadTimeoutWheel readTimeoutWheel, long now) {
        this.key = key;
        this.channel = channel;
        this.protocolHandler = protocolHandler;
        this.readTimeoutWheel = readTimeoutWheel;
        this.lastReadNanos = now;
    }

    private int pendingBytes;

    boolean read(TransportBuffer buffer, long now) throws IOException {
        this.readBytesThisCall = 0;
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
                this.readBytesThisCall += written;
                this.lastReadNanos = now;
                this.readDeadlineNanos = now + READ_IDLE_TIMEOUT_NANOS;
                this.readTimeoutWheel.update(this);
            }

            final int available = this.pendingBytes;
            this.pendingBytes = 0;
            bytes += available;
            reads++;

            this.protocolHandler.onBytes(this.writer.reader(), available);
            if (this.protocolHandler.payloadComplete()) {
                final int requestLength = this.protocolHandler.requestLength();
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
                this.pendingBytes = available; // reset pending bytes correctly
                return false;
            }
        }

        return false;
    }

    private boolean publish() {
        final long sequence = this.responseBuffer.offer(this.writer);
        if (sequence == -1) {
            // TODO disable OP_READ?
            return false; // response buffer is full, backpressue is done by TCP socket buffers
        }
        final TransportBuffer.Writer writer = this.writer;
        final TransportBuffer.Reader reader = writer.reader();
        final int limit = reader.position();
        reader.limit(limit); // limit the reader to discard multiple requests written if any
        reader.reset();
        this.protocolHandler.reset();
        this.writer = null;
        this.published = writer;
        this.publishedSequence = sequence;
        return true;
    }

    private void discard() {
        if (this.writer != null) {
            this.writer.release();
            this.writer = null;
            this.protocolHandler.reset();
        }
    }

    int write(TransportBuffer.Reader reader, long now) throws IOException {
        final int written = reader.write(this.channel);
        if (written > 0) {
            this.lastWriteNanos = now;
        }
        return written;
    }

    void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        if (this.response != null) {
            this.response.writer().release();
            this.response = null;
        }
        // TODO proper cleanup for READY responses in responseBuffer!
        try {
            this.channel.close();
        } catch (IOException _) {
            // best effort shutdown
        }
        this.key.cancel();
        discard();
    }

    int pendingBytes() {
        return this.pendingBytes;
    }

    boolean isClosed() {
        return this.closed;
    }

}
