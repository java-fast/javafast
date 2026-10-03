package io.javafast.transport;

public final class HttpProtocolHandler implements ProtocolHandler {

    // TODO implement checks here
    private static final int MAX_REQUEST_SIZE = Integer.getInteger("javafast.max.request.size", 10 * (1 << 20)); // aka 10MB
    private static final int MAX_BODY_SIZE = Integer.getInteger("javafast.max.request.body.size", 1 << 23); // aka 8MB

    private static final byte CR = '\r';
    private static final byte LF = '\n';
    private static final byte SPACE = ' ';
    private static final byte HTAB = '\t';

    private static final String CONTENT_LENGTH = "content-length";
    private static final String TRANSFER_ENCODING = "transfer-encoding";
    private static final String TRANSFER_CODE_CHUNKED = "chunked";

    private enum State {
        HEADERS,
        BODY,
        COMPLETE
    }

    private State state = State.HEADERS;

    private long contentLength;
    private boolean contentLengthFound;
    private long bodyRemaining;

    @Override
    public void onBytes(TransportBuffer.Reader reader, int bytes) {
        if (this.state == State.COMPLETE) {
            return;
        }

        if (this.state == State.HEADERS) {
            parseHeaders(reader);
        }

        if (this.state == State.BODY) {
            final int count = (int) Math.min(bytes, this.bodyRemaining);

            reader.skip(count);
            this.bodyRemaining -= count;

            if (this.bodyRemaining == 0) {
                this.state = State.COMPLETE;
            }
        }
    }

    private void parseHeaders(TransportBuffer.Reader reader) {
        reader.skip(reader.indexOf(CR, LF) + 2); // skip request line
        int consumed;
        while (true) {
            consumed = 0;
            final int length = reader.indexOf(CR, LF);
            if (length < 0) {
                return;
            }
            // Empty line => end of headers.
            if (length == 0) {
                reader.skip(2);
                if (!this.contentLengthFound || this.contentLength == 0) {
                    this.state = State.COMPLETE;
                    return;
                }
                this.bodyRemaining = this.contentLength;
                this.state = State.BODY;
                return;
            }
            // Check Content-Length before skipping the header.
            if (reader.matchesAsciiIgnoreCase(CONTENT_LENGTH)) {
                consumed = parseContentLength(reader);
            }
            // Skip this header line including CRLF.
            reader.skip(length - consumed + 2);
        }
    }

    private int parseContentLength(TransportBuffer.Reader reader) {
        int consumed = CONTENT_LENGTH.length() + 1;
        reader.skip(consumed); // skip "Content-Length:"
        // clear leading OWS
        while (reader.consume(SPACE) || reader.consume(HTAB)) {
            consumed++;
        }
        final int valueLength = reader.indexOf(CR, LF);
        long value = 0;
        for (int i = 0; i < valueLength; i++) {
            final int c = reader.getUnsignedByte();
            consumed++;
            if (c < '0' || c > '9') {
                break;
            }
            value = value * 10 + (c - '0');
        }
        this.contentLength = value;
        this.contentLengthFound = true;
        // we don't need trailing OWS since the outer loop will skip it
        return consumed;
    }

    @Override
    public boolean payloadComplete() {
        return this.state == State.COMPLETE;
    }

    @Override
    public void reset() {
        this.state = State.HEADERS;
        this.contentLength = 0;
        this.contentLengthFound = false;
        this.bodyRemaining = 0;
    }
}


