package io.javafast.internal;


import io.javafast.transport.TransportBuffer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

final class TransportBufferOutputStream extends OutputStream {

    private final TransportBuffer.Writer writer;
    private boolean closed;

    TransportBufferOutputStream(TransportBuffer.Writer writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public void write(int value) throws IOException {
        ensureOpen();
        this.writer.writeByte(value);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        ensureOpen();
        Objects.checkFromIndexSize(offset, length, bytes.length);
        if (length == 0) {
            return;
        }
        this.writer.writeBytes(bytes, offset, length);
    }

    @Override
    public void flush() throws IOException {
        ensureOpen();
        // The response is buffered in TransportBuffer.
        // Nothing is written to the socket here.
    }

    @Override
    public void close() {
        // Does not release the underlying block chain.
        this.closed = true;
    }

    private void ensureOpen() throws IOException {
        if (this.closed) {
            throw new IOException("Stream is closed");
        }
    }
}
