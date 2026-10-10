package io.javafast.transport.source;

import io.javafast.transport.TransportBuffer;
import io.javafast.transport.TransportTestSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP 1.1 Request Encoder for benchmark testing
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
public class HttpRequestSource implements TransportTestSource {

    private String method;
    private String path;
    private String requestBody;
    private Map<String, String> headers;

    private InputStream body;
    private byte[] headBytes;
    private int headOffset;

    final byte[] chunk = new byte[8192]; // chunk buffer

    private void initialize() {
        if (this.headBytes != null) {
            return;
        }
        final var requestLineAndHeaders = new StringBuilder()
            .append(this.method)
            .append(' ')
            .append(this.path)
            .append(" HTTP/1.1\r\n");

        if (this.headers != null) {
            for (var entry : this.headers.entrySet()) {
                requestLineAndHeaders
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append("\r\n");
            }
        }
        requestLineAndHeaders.append("\r\n");

        this.headBytes = requestLineAndHeaders.toString().getBytes(StandardCharsets.ISO_8859_1);
        if (this.requestBody != null) {
            this.body = new ByteArrayInputStream(this.requestBody.getBytes(StandardCharsets.ISO_8859_1));
        } else {
            this.body = InputStream.nullInputStream();
        }
        this.headOffset = 0;
    }

    private void reset() {
        this.method = null;
        this.path = null;
        this.requestBody = null;
        this.headers = null;
        this.body = null;
        this.headBytes = null;
        this.headOffset = 0;
    }

    @Override
    public int read(ByteBuffer destination) {
        if (this.method == null || this.path == null) {
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

    public TransportBuffer.Writer encode(TransportBuffer target, String method, String path, String requestBody, Map<String, String> headers) {
        reset();
        this.method = method;
        this.path = path;
        this.requestBody = requestBody;
        this.headers = headers;
        return copyTo(target);
    }

}
