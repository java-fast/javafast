package io.javafast.transport;

import io.javafast.http.HttpMethod;

import java.nio.ByteOrder;

/**
 * HTTP 1.1 Request Decoder
 */
public class HttpRequestDecoder {

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private static final byte SPACE = ' ';
    private static final short CRLF = LITTLE_ENDIAN ? (short) 0x0A0D : (short) 0x0D0A;

    private static final long GET = LITTLE_ENDIAN ? 0x0000000000544547L : 0x4745540000000000L;
    private static final long PUT = LITTLE_ENDIAN ? 0x0000000000545550L : 0x5055540000000000L;
    private static final long POST = LITTLE_ENDIAN ? 0x0000000054534F50L : 0x504F535400000000L;
    private static final long HEAD = LITTLE_ENDIAN ? 0x0000000044414548L : 0x4845414400000000L;
    private static final long PATCH = LITTLE_ENDIAN ? 0x0000000048435441L : 0x5041544348000000L;
    private static final long TRACE = LITTLE_ENDIAN ? 0x0000000045434152L : 0x5452414345000000L;
    private static final long DELETE = LITTLE_ENDIAN ? 0x0000000045544544L : 0x44454C4554450000L;
    private static final long OPTIONS = LITTLE_ENDIAN ? 0x00534E4F4954504FL : 0x4F5054494F4E5300L;
    private static final long CONNECT = LITTLE_ENDIAN ? 0x005443454E4E4F43L : 0x434F4E4E45435400L;

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

    private int methodStart;
    private int methodLength;
    private HttpMethod method;

    private int targetStart;
    private int targetLength;

    private int headersStart;
    private int headersLength;

    private int bodyStart;
    private int bodyLength;

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
                throw MalformedRequestException.malformed();
            }
        }
        this.reader.skip(this.methodLength + 1);

        this.targetStart = this.methodLength + 1;
        final int index = this.reader.indexOf(SPACE);
        if (index < 0) {
            throw MalformedRequestException.malformed();
        }
        this.targetLength = index - this.targetStart;
        this.reader.skip(this.targetLength + 1);

        if (!this.reader.consumeAscii("HTTP/1.1")) {
            throw MalformedRequestException.notSupported();
        }

        if (!this.reader.consume(CRLF)) {
            throw MalformedRequestException.malformed();
        }
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

}
