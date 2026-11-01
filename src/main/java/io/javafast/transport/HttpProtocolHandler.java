package io.javafast.transport;

import io.javafast.http.HttpRequestException;

public final class HttpProtocolHandler implements ProtocolHandler {

    private static final int MAX_REQUEST_SIZE = Integer.getInteger("javafast.max.request.size", 1 << 22); // aka 4MB
    private static final int MAX_HEADER_SIZE = Integer.getInteger("javafast.max.request.header.size", 1 << 13); // aka 8KB
    private static final int MAX_BODY_SIZE = Integer.getInteger("javafast.max.request.body.size", 1 << 20); // aka 1MB

    private static final byte CR = '\r';
    private static final byte LF = '\n';
    private static final byte SPACE = ' ';
    private static final byte HTAB = '\t';

    private static final byte[] CONTENT_LENGTH = new byte[]{'c', 'o', 'n', 't', 'e', 'n', 't', '-', 'l', 'e', 'n', 'g', 't', 'h', ':'};
    private static final byte[] TRANSFER_ENCODING = new byte[]{'t', 'r', 'a', 'n', 's', 'f', 'e', 'r', '-', 'e', 'n', 'c', 'o', 'd', 'i', 'n', 'g', ':'};

    private static final String TRANSFER_CODE_CHUNKED = "chunked";

    private enum State {
        REQUEST_LINE,
        HEADERS,
        BODY,
        COMPLETE
    }

    private State state = State.REQUEST_LINE;

    private int headerBytesRead;
    private int requestLength; // MAX_REQUEST_SIZE keeps us in Integer limits
    private boolean contentLengthFound;
    private int contentLength; // MAX_REQUEST_SIZE keeps us in Integer limits
    private int bodyRemaining; // MAX_REQUEST_SIZE keeps us in Integer limits

    @Override
    public void onBytes(TransportBuffer.Reader reader, int bytes) {
        if (this.state == State.COMPLETE) {
            return;
        }

        this.requestLength += bytes;
        if (this.requestLength > MAX_REQUEST_SIZE) {
            throw HttpRequestException.REQUEST_TOO_LARGE;
        }

        if (this.state == State.REQUEST_LINE) {
            final int lineEnd = reader.indexOf(CR, LF);
            if (lineEnd < 0) return;
            if (lineEnd > MAX_HEADER_SIZE) throw HttpRequestException.REQUEST_TOO_LARGE;
            reader.skip(lineEnd + 2); // skip request line
            this.headerBytesRead = lineEnd + 2;
            this.state = State.HEADERS;
        }

        if (this.state == State.HEADERS) {
            parseHeaders(reader);
        }

        if (this.state == State.BODY) {
            final int skip = Math.min(reader.remaining(), this.bodyRemaining);
            reader.skip(skip);
            this.bodyRemaining -= skip;
            if (this.bodyRemaining == 0) {
                this.state = State.COMPLETE;
            }
        }
    }

    private void parseHeaders(TransportBuffer.Reader reader) {
        int headerLength;
        while (this.state == State.HEADERS && reader.position() < this.requestLength) {
            if (this.headerBytesRead > MAX_HEADER_SIZE) {
                throw HttpRequestException.REQUEST_TOO_LARGE;
            }
            headerLength = reader.indexOf(CR, LF);
            if (headerLength < 0) {
                if (reader.limit() > MAX_HEADER_SIZE) {
                    throw HttpRequestException.REQUEST_TOO_LARGE;
                }
                return;
            }
            // Empty line => end of headers.
            if (headerLength == 0) {
                reader.skip(2);
                if (!this.contentLengthFound || this.contentLength == 0) {
                    this.state = State.COMPLETE;
                    return;
                }
                this.bodyRemaining = this.contentLength;
                if (this.bodyRemaining > MAX_BODY_SIZE) {
                    throw HttpRequestException.REQUEST_TOO_LARGE;
                }
                this.state = State.BODY;
                return;
            }
            if (reader.matchesAsciiIgnoreCase(CONTENT_LENGTH)) {
                parseContentLength(reader, headerLength);
            } else if (reader.matchesAsciiIgnoreCase(TRANSFER_ENCODING)) {
                parseTransferEncoding(reader, headerLength);
            } else {
                reader.skip(headerLength + 2); // +CRLF
            }
            this.headerBytesRead += headerLength + 2;
        }
    }

    private void parseContentLength(TransportBuffer.Reader reader, int headerLength) {
        if (this.contentLengthFound) {
            throw HttpRequestException.MALFORMED;
        }
        reader.skip(CONTENT_LENGTH.length); // skip "Content-Length:"
        headerLength -= CONTENT_LENGTH.length;
        // clear leading OWS
        while (reader.consume(SPACE) || reader.consume(HTAB)) {
            headerLength--;
        }
        int digits = 0;
        int value = 0;
        final int length = headerLength;
        for (int i = 0; i < length; i++) {
            headerLength--;
            final int c = reader.getUnsignedByte();
            if (c < '0' || c > '9') {
                break;
            }
            final int digit = c - '0';
            if (value > (Integer.MAX_VALUE - digit) / 10) {
                throw HttpRequestException.MALFORMED;
            }
            value = value * 10 + digit;
            digits++;
        }
        if (digits == 0 || value < 0) {
            throw HttpRequestException.MALFORMED; // no digit
        }
        if (value > MAX_BODY_SIZE) {
            throw HttpRequestException.REQUEST_TOO_LARGE;
        }
        this.contentLength = value;
        this.contentLengthFound = true;
        reader.skip(headerLength + 2); // +CRLF
    }

    private void parseTransferEncoding(TransportBuffer.Reader reader, int headerLength) {
        // TODO implement chunked parsing
        reader.skip(headerLength + 2); // +CRLF
    }

    @Override
    public boolean payloadComplete() {
        return this.state == State.COMPLETE;
    }

    @Override
    public void reset() {
        this.state = State.REQUEST_LINE;
        this.headerBytesRead = 0;
        this.requestLength = 0;
        this.contentLength = 0;
        this.contentLengthFound = false;
        this.bodyRemaining = 0;
    }
}


