package io.javafast.transport;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 1.1 Request Encoder for test to consume for Jdk's HttpExchange
 */
public class HttpRequestEncoder implements TransportTestSource {

    private HttpExchange source;
    private InputStream body;
    private byte[] headBytes;
    private int headOffset;

    private void initialize() {
        if (this.headBytes != null) {
            return;
        }
        final var requestLineAndHeaders = new StringBuilder()
            .append(this.source.getRequestMethod())
            .append(' ')
            .append(this.source.getRequestURI().toASCIIString())
            .append(" HTTP/1.1\r\n");

        for (var entry : this.source.getRequestHeaders().entrySet()) {
            requestLineAndHeaders.append(entry.getKey()).append(": ");
            boolean first = true;
            for (var value : entry.getValue()) {
                if (!first) {
                    requestLineAndHeaders.append(", ");
                }
                requestLineAndHeaders.append(value);
                first = false;
            }
            requestLineAndHeaders.append("\r\n");
        }
        requestLineAndHeaders.append("\r\n");

        this.headBytes = requestLineAndHeaders.toString().getBytes(StandardCharsets.ISO_8859_1);
        this.body = this.source.getRequestBody();
        this.headOffset = 0;
    }

    private void reset() {
        this.source = null;
        this.body = null;
        this.headBytes = null;
        this.headOffset = 0;
    }

    @Override
    public int read(ByteBuffer destination) {
        if (this.source == null) {
            return -1;
        }
        initialize();

        int written = 0;
        if (this.headOffset < this.headBytes.length && destination.hasRemaining()) {
            final int toWrite = Math.min(destination.remaining(), this.headBytes.length - this.headOffset);
            destination.put(this.headBytes, this.headOffset, toWrite);
            this.headOffset += toWrite;
            written += toWrite;
        }

        final byte[] chunk = new byte[8192]; // chunk buffer
        while (destination.hasRemaining() && this.body != null) {
            final int read;
            try {
                read = this.body.read(chunk, 0, Math.min(destination.remaining(), 8192));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read request body", exception);
            }
            if (read == -1) {
                reset();
                return written == 0 ? -1 : written;
            }
            destination.put(chunk, 0, read);
            written += read;
        }

        return written;
    }

    public TransportBuffer.Writer encode(HttpExchange source, TransportBuffer target) {
        reset();
        this.source = source;
        return copyTo(target);
    }

}
