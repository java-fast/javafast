package io.javafast.transport;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 1.1 Request Encoder for test to consume for Jdk's HttpExchange
 * Example HTTP/1.1 Request:
 * <p>
 * POST /users/42?foo=bar HTTP/1.1\r\n
 * Host: example.com\r\n
 * Content-Type: application/json\r\n
 * Content-Length: 19\r\n
 * X-Foo: bar\r\n
 * \r\n
 * {"name":"JavaFast"}
 * </p>
 */
public class HttpTestRequestEncoder implements TransportTestSource {

    private HttpExchange source;
    private InputStream body;
    private byte[] headBytes;
    private int headOffset;

    final byte[] chunk = new byte[8192]; // chunk buffer

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
            for (var value : entry.getValue()) {
                requestLineAndHeaders
                    .append(entry.getKey())
                    .append(": ")
                    .append(value)
                    .append("\r\n");
            }
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

        while (destination.hasRemaining() && this.body != null) {
            final int read;
            try {
                read = this.body.read(this.chunk, 0, Math.min(destination.remaining(), 8192));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read request body", exception);
            }
            if (read == -1) {
                reset();
                return written == 0 ? -1 : written;
            }
            destination.put(this.chunk, 0, read);
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
