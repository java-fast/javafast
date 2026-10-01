package io.javafast.transport;

import io.javafast.http.HttpMethod;

public class Http1RequestDecoder {

    private static final short CRLF = 0x0D0A;

    private TransportBuffer.Reader reader;

    private int methodStart;
    private int methodEnd;

    private int targetStart;
    private int targetEnd;

    private int headersStart;
    private int headersEnd;

    private int bodyStart;
    private int bodyEnd;

    private long contentLength;
    private boolean chunked;

    public void parse(TransportBuffer.Reader reader) {
        this.reader = reader;

        this.methodStart = -1;
        this.methodEnd = -1;
        this.targetStart = -1;
        this.targetEnd = -1;
        this.headersStart = -1;
        this.headersEnd = -1;
        this.bodyStart = -1;
        this.bodyEnd = -1;
        this.contentLength = 0L;
        this.chunked = false;

        final long end = reader.limit();
        if (end == 0) {
            return;
        }

        this.methodStart = 0;
        this.methodEnd = reader.indexOf((byte) ' ', 0);
        if (this.methodEnd < 0) {
            return;
        }

        this.targetStart = this.methodEnd + 1;
        this.targetEnd = reader.indexOf((byte) ' ', this.targetStart);
        if (this.targetEnd < 0) {
            return;
        }

        final int requestLineEnd = findCrlf(this.targetEnd + 1, (int) end);
        if (requestLineEnd < 0) {
            return;
        }

        this.headersStart = requestLineEnd + 2;

        final int headerTerminator = findHeaderTerminator(this.headersStart, (int) end);
        if (headerTerminator < 0) {
            return;
        }

        this.headersEnd = headerTerminator + 1;
        this.bodyStart = headerTerminator + 4;

        parseHeaders(this.headersStart, headerTerminator);

        if (this.chunked) {
            this.bodyEnd = (int) end - 1;
            return;
        }

        if (this.contentLength > 0) {
            final long expectedBodyEnd = (long) this.bodyStart + this.contentLength - 1;
            this.bodyEnd = (int) Math.min(end - 1, expectedBodyEnd);
            return;
        }

        this.bodyEnd = this.bodyStart - 1;
    }

    public HttpMethod getMethod() {
        if (this.reader == null || this.methodStart < 0 || this.methodEnd <= this.methodStart) {
            return null;
        }
        return HttpMethod.of(this.reader.getAscii(this.methodStart, this.methodEnd - this.methodStart));
    }

    public String getTarget() {
        if (this.reader == null || this.targetStart < 0 || this.targetEnd < this.targetStart) {
            return null;
        }
        return this.reader.getAscii(this.targetStart, this.targetEnd - this.targetStart);
    }

    private int findHeaderTerminator(int start, int endExclusive) {
        int offset = start;
        while (offset >= 0 && offset + 3 < endExclusive) {
            offset = this.reader.indexOf((byte) '\r', offset);
            if (offset < 0 || offset + 3 >= endExclusive) {
                return -1;
            }
            if (this.reader.asciiEquals(offset, "\r\n\r\n")) {
                return offset;
            }
            offset++;
        }
        return -1;
    }

    private int findCrlf(int start, int endExclusive) {
        int offset = start;
        while (offset >= 0 && offset + 1 < endExclusive) {
            offset = this.reader.indexOf((byte) '\r', offset);
            if (offset < 0 || offset + 1 >= endExclusive) {
                return -1;
            }
            if (this.reader.asciiEquals(offset, "\r\n")) {
                return offset;
            }
            offset++;
        }
        return -1;
    }

    private void parseHeaders(int start, int endExclusive) {
        int offset = start;
        while (offset >= 0 && offset < endExclusive) {
            final int lineEnd = findCrlf(offset, endExclusive + 2);
            if (lineEnd < 0 || lineEnd <= offset) {
                return;
            }

            final int separator = this.reader.indexOf((byte) ':', offset);
            if (separator > offset && separator < lineEnd) {
                final String headerName = this.reader.getAscii(offset, separator - offset).trim();
                final String headerValue = this.reader.getAscii(separator + 1, lineEnd - separator - 1).trim();

                if ("Content-Length".equalsIgnoreCase(headerName)) {
                    try {
                        this.contentLength = Long.parseLong(headerValue);
                    } catch (NumberFormatException ignored) {
                        this.contentLength = 0L;
                    }
                } else if ("Transfer-Encoding".equalsIgnoreCase(headerName)
                    && headerValue.toLowerCase().contains("chunked")) {
                    this.chunked = true;
                }
            }

            offset = lineEnd + 2;
        }
    }

}
