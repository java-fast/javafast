package io.javafast.transport;

import io.javafast.http.HttpMethod;

import java.nio.ByteOrder;

/**
 * HTTP 1.1 Request Decoder
 */
public class HttpRequestDecoder {

    private static final int MAX_HEADER_COUNT = Integer.getInteger("javafast.max.header.count", 64);
    private static final int MAX_REQUEST_SIZE = Integer.getInteger("javafast.max.request.size", 10 * (1 << 20)); // aka 10MB
    private static final int MAX_BODY_SIZE = Integer.getInteger("javafast.max.request.body.size", 1 << 23); // aka 8MB

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private static final long GET = LITTLE_ENDIAN ? 0x0000000000544547L : 0x4745540000000000L;
    private static final long PUT = LITTLE_ENDIAN ? 0x0000000000545550L : 0x5055540000000000L;
    private static final long POST = LITTLE_ENDIAN ? 0x0000000054534F50L : 0x504F535400000000L;
    private static final long HEAD = LITTLE_ENDIAN ? 0x0000000044414548L : 0x4845414400000000L;
    private static final long PATCH = LITTLE_ENDIAN ? 0x0000000048435441L : 0x5041544348000000L;
    private static final long TRACE = LITTLE_ENDIAN ? 0x0000000045434152L : 0x5452414345000000L;
    private static final long DELETE = LITTLE_ENDIAN ? 0x0000000045544544L : 0x44454C4554450000L;
    private static final long OPTIONS = LITTLE_ENDIAN ? 0x00534E4F4954504FL : 0x4F5054494F4E5300L;
    private static final long CONNECT = LITTLE_ENDIAN ? 0x005443454E4E4F43L : 0x434F4E4E45435400L;

    private static final byte CR = '\r';
    private static final byte LF = '\n';
    private static final byte SPACE = ' ';
    private static final byte SEMICOLON = ':';

    private static final String CONTENT_LENGTH = "content-length";
    private static final String TRANSFER_ENCODING = "transfer-encoding";
    private static final String TRANSFER_CODE_CHUNKED = "chunked";

    private static long hasSpace(long word) {
        final long x = word ^ 0x2020202020202020L;
        return (x - 0x0101010101010101L) & ~x & 0x8080808080808080L;
    }

    private static HttpMethod parseMethod(long word) {
        final long spaces = hasSpace(word);
        final int length = LITTLE_ENDIAN
            ? Long.numberOfTrailingZeros(spaces) >>> 3
            : Long.numberOfLeadingZeros(spaces) >>> 3;

        final long mask = LITTLE_ENDIAN
            ? -1L >>> ((8 - length) << 3)
            : -1L << ((8 - length) << 3);
        final long value = word & mask;
        return switch (length) {
            case 3 -> {
                if (value == GET) yield HttpMethod.GET;
                if (value == PUT) yield HttpMethod.PUT;
                yield null;
            }
            case 4 -> {
                if (value == POST) yield HttpMethod.POST;
                if (value == HEAD) yield HttpMethod.HEAD;
                yield null;
            }
            case 5 -> {
                if (value == PATCH) yield HttpMethod.PATCH;
                if (value == TRACE) yield HttpMethod.TRACE;
                yield null;
            }
            case 6 -> {
                if (value == DELETE) yield HttpMethod.DELETE;
                yield null;
            }
            case 7 -> {
                if (value == OPTIONS) yield HttpMethod.OPTIONS;
                if (value == CONNECT) yield HttpMethod.CONNECT;
                yield null;
            }
            default -> null;
        };
    }

    private TransportBuffer.Reader reader;

    private final int[] headers = new int[MAX_HEADER_COUNT * 4]; // [ NAME_START, NAME_LENGTH, VALUE_START, VALUE_LENGTH, ... ]

    private int methodStart;
    private int methodLength;
    private HttpMethod method;

    private int targetStart;
    private int targetLength;

    private int headersStart;
    private int headersLength;

    private int bodyStart;
    private int bodyLength;

    /**
     * TODO
     * if (contentLength > MAX_BODY_SIZE) {
     *     throw ...
     * }
     */
    private long contentLength;
    private boolean chunked;

    public void parse(TransportBuffer.Reader reader) {
        this.reader = reader;
        this.reader.reset();
        this.methodStart = -1;
        this.methodLength = 0;
        this.method = null;
        this.targetStart = -1;
        this.targetLength = 0;
        this.headersStart = -1;
        this.headersLength = 0;
        this.bodyStart = -1;
        this.bodyLength = 0;
        this.contentLength = 0L;
        this.chunked = false;

        if (!reader.hasRemaining()) {
            return;
        }
        parseRequestLine();
        parseHeaders();
        parseBody();
    }

    private void parseRequestLine() {
        this.methodStart = 0;
        this.method = parseMethod(this.reader.getLong());
        this.reader.reset();
        if (this.method != null) { // fast path for known HTTP methods
            this.methodLength = this.method.name().length();
        } else { // slower path
            this.methodLength = this.reader.indexOf(SPACE);
            if (this.methodLength < 0) {
                throw HttpRequestException.malformed();
            }
        }
        this.reader.skip(this.methodLength + 1);

        this.targetStart = this.methodLength + 1;
        this.targetLength = this.reader.indexOf(SPACE);
        if (this.targetLength < 0) {
            throw HttpRequestException.malformed();
        }
        this.reader.skip(this.targetLength + 1);

        if (!this.reader.consumeAscii("HTTP/1.1")) {
            throw HttpRequestException.notSupported();
        }

        if (!this.reader.consume(CR, LF)) {
            throw HttpRequestException.malformed();
        }
    }

    private void parseHeaders() {
        this.headersStart = this.reader.position();

        int headerCount = 0;
        int nameStart;
        int nameLength;
        int valueStart;
        int valueLength;
        while (true) {
            if (headerCount >= MAX_HEADER_COUNT) {
                throw HttpRequestException.tooManyHeaders();
            }
            // Empty line => end of headers
            if (this.reader.consume(CR, LF)) {
                this.headersLength = this.reader.position() - this.headersStart;
                this.bodyStart = this.reader.position();
                return;
            }

            nameStart = this.reader.position();
            nameLength = this.reader.indexOf(SEMICOLON);
            if (nameLength <= 0) { // header name must not be empty
                throw HttpRequestException.malformed();
            }

            if (this.reader.matchesAsciiIgnoreCase(CONTENT_LENGTH)) {
                this.reader.skip(nameLength + 1); // +1 for ':'
                skipOWS();
                valueStart = this.reader.position();
                valueLength = parseContentLength();
            } else if (this.reader.matchesAsciiIgnoreCase(TRANSFER_ENCODING)) {
                this.reader.skip(nameLength + 1); // +1 for ':'
                skipOWS();
                valueStart = this.reader.position();
                valueLength = parseTransferEncoding();
            } else {
                this.reader.skip(nameLength + 1); // +1 for ':'
                skipOWS();
                valueStart = this.reader.position();
                valueLength = this.reader.indexOf(CR, LF);
                if (valueLength < 0) {
                    throw HttpRequestException.malformed();
                }
                this.reader.skip(valueLength);
            }
            // Every header must end with CRLF
            if (!this.reader.consume(CR, LF)) {
                throw HttpRequestException.malformed();
            }

            addHeader(headerCount++ << 2, nameStart, nameLength, valueStart, valueLength);
        }
    }

    private void skipOWS() {
        while (this.reader.consume(SPACE)) {
            // OWS
        }
    }

    private void addHeader(int offset, int nameStart, int nameLength, int valueStart, int valueLength) {
        this.headers[offset] = nameStart;
        this.headers[offset + 1] = nameLength;
        this.headers[offset + 2] = valueStart;
        this.headers[offset + 3] = valueLength;
    }

    private int parseContentLength() {
        final int valueLength = this.reader.indexOf(CR, LF);
        if (valueLength < 0) {
            throw HttpRequestException.malformed();
        }
        long value = 0;
        for (int i = 0; i < valueLength; i++) {
            final int c = this.reader.getUnsignedByte();
            if (c < '0' || c > '9') {
                throw HttpRequestException.malformed();
            }
            value = value * 10 + (c - '0');
        }
        this.contentLength = value;
        return valueLength; // parsed length
    }

    private int parseTransferEncoding() {
        final int valueLength = this.reader.indexOf(CR, LF);
        if (valueLength < 0) {
            throw HttpRequestException.malformed();
        }
        this.reader.skip(valueLength - TRANSFER_CODE_CHUNKED.length());
        if (!this.reader.matchesAsciiIgnoreCase(TRANSFER_CODE_CHUNKED)) {
            throw HttpRequestException.unsupportedTransferEncoding();
        }
        this.reader.skip(TRANSFER_CODE_CHUNKED.length());
        this.chunked = true;
        return valueLength; // parsed length
    }

    private void parseBody() {
        if (this.chunked) {
            parseChunkedBody();
        } else if (this.contentLength > 0) {
            parseFixedLengthBody();
        } else {
            this.bodyStart = this.reader.position();
            this.bodyLength = 0;
        }
    }

    private void parseFixedLengthBody() {
        if (this.contentLength > MAX_BODY_SIZE) {
            throw HttpRequestException.requestTooLarge();
        }
        this.bodyStart = this.reader.position();
        this.bodyLength = (int) this.contentLength;
        if (this.reader.remaining() < this.bodyLength) {
            throw HttpRequestException.malformed();
        }
        this.reader.skip(this.bodyLength);
    }

    private void parseChunkedBody() {
        // TODO
    }

    public HttpMethod getMethod() {
        if (this.method != null) {
            return this.method;
        }
        return HttpMethod.of(this.reader.getAscii(this.methodStart, this.methodLength));
    }

    public String getTarget() {
        return this.reader.getAscii(this.targetStart, this.targetLength);
    }

    public String getHeaderName(int index) {
        final int offset = index << 2;
        return this.reader.getAscii(this.headers[offset], this.headers[offset + 1]);
    }

    public String getHeader(int index) {
        final int offset = index << 2;
        return this.reader.getAscii(this.headers[offset + 2], this.headers[offset + 3]);
    }

    public String getBody() {
        if (this.bodyLength == 0) {
            return "";
        }
        return this.reader.getAscii(this.bodyStart, this.bodyLength);
    }

}
