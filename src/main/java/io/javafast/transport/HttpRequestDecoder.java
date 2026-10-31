package io.javafast.transport;

import io.javafast.http.HttpMethod;
import io.javafast.http.HttpRequestException;
import io.javafast.internal.ByteString;
import io.javafast.util.HostValidator;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 1.1 Request Decoder
 * MAX_BODY_SIZE is checked in ProtocolHandler, so we safely skip it here
 */
public class HttpRequestDecoder {

    private static final int MAX_HEADER_COUNT = Integer.getInteger("javafast.max.header.count", 64);

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

    private static final byte[] EMPTY_ARRAY = new byte[0];
    private static final byte CR = '\r';
    private static final byte LF = '\n';
    private static final byte SPACE = ' ';
    private static final byte HTAB = '\t';
    private static final byte COLON = ':';

    // TODO move into Headers?
    private static final byte[] HOST = new byte[] {'h', 'o', 's', 't', ':'};
    private static final byte[] CONTENT_LENGTH = new byte[] {'c', 'o', 'n', 't', 'e', 'n', 't', '-', 'l', 'e', 'n', 'g', 't', 'h', ':'};
    private static final byte[] TRANSFER_ENCODING = new byte[] {'t', 'r', 'a', 'n', 's', 'f', 'e', 'r', '-', 'e', 'n', 'c', 'o', 'd', 'i', 'n', 'g', ':'};

    private static final byte[] HTTP_VERSION = new byte[] {'H', 'T', 'T', 'P', '/', '1', '.', '1'};
    private static final byte[] TRANSFER_CODE_CHUNKED = new byte[] {'c', 'h', 'u', 'n', 'k', 'e', 'd'};

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

    private static boolean isTchar(byte value) {
        return (value >= 'a' && value <= 'z')
            || (value >= 'A' && value <= 'Z')
            || (value >= '0' && value <= '9')
            || switch (value) {
            case '!', '#', '$', '%', '&', '\'', '*', '+',
                 '-', '.', '^', '_', '`', '|', '~' -> true;
            default -> false;
        };
    }

    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;

    /**
     * Vector mask for header names allowed chars in HTTP/1.1
     */
    private static VectorMask<Byte> tcharMask(ByteVector v) {
        return v.compare(VectorOperators.EQ, (byte) '!')
            .or(v.compare(VectorOperators.GE, (byte) '#')
                .and(v.compare(VectorOperators.LE, (byte) '&')))
            .or(v.compare(VectorOperators.GE, (byte) '*')
                .and(v.compare(VectorOperators.LE, (byte) '+')))
            .or(v.compare(VectorOperators.GE, (byte) '-')
                .and(v.compare(VectorOperators.LE, (byte) '.')))
            .or(v.compare(VectorOperators.GE, (byte) '0')
                .and(v.compare(VectorOperators.LE, (byte) '9')))
            .or(v.compare(VectorOperators.GE, (byte) 'A')
                .and(v.compare(VectorOperators.LE, (byte) 'Z')))
            .or(v.compare(VectorOperators.GE, (byte) '^')
                .and(v.compare(VectorOperators.LE, (byte) 'z')))
            .or(v.compare(VectorOperators.EQ, (byte) '\''))
            .or(v.compare(VectorOperators.EQ, (byte) '|'))
            .or(v.compare(VectorOperators.EQ, (byte) '~'));
    }

    private static int firstNonTchar(MemorySegment segment, int offset, int length) {
        int i = 0;
        final int loopBound = SPECIES.loopBound(length);
        for (; i < loopBound; i += SPECIES.length()) {
            final ByteVector v = ByteVector.fromMemorySegment(SPECIES, segment, offset + i, ByteOrder.nativeOrder());
            final VectorMask<Byte> invalid = tcharMask(v).not();
            if (invalid.anyTrue()) {
                return i + invalid.firstTrue();
            }
        }
        for (; i < length; i++) {
            if (!isTchar(segment.get(ValueLayout.JAVA_BYTE, offset + i))) {
                return i;
            }
        }
        return -1;
    }

    private TransportBuffer.Reader reader;

    // TODO keep known Headers in another array with Enum/Object keys?

    private final int[] headers = new int[MAX_HEADER_COUNT * 4]; // [ NAME_START, NAME_LENGTH, VALUE_START, VALUE_LENGTH, ... ]

    private int methodStart;
    private int methodLength;
    private HttpMethod method;

    private int targetStart;
    private int targetLength;

    private int headersStart;
    private int headersLength;
    private int headersCount;

    private int bodyStart;
    private int bodyLength;

    private int contentLength; // due to MAX_REQUEST_SIZE check int is enough
    private boolean chunked;

    private final ByteString target = new ByteString(32);
    private final ByteString host = new ByteString(32);

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
        this.headersCount = 0;
        this.bodyStart = -1;
        this.bodyLength = 0;
        this.contentLength = 0;
        this.chunked = false;
        this.target.clear();
        this.host.clear();

        if (!reader.hasRemaining()) {
            return;
        }
        parseRequestLine();
        parseHeaders();
        if (this.headersCount == 0 || this.host.length() == 0) {
            throw HttpRequestException.MALFORMED;
        }
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
                throw HttpRequestException.MALFORMED;
            }
        }
        this.reader.skip(this.methodLength + 1);

        this.targetStart = this.methodLength + 1;
        this.targetLength = this.reader.indexOf(SPACE);
        if (this.targetLength < 0) {
            throw HttpRequestException.MALFORMED;
        }
        this.target.reset(this.reader, this.targetStart, this.targetLength);
        this.reader.skip(this.targetLength + 1);

        if (!this.reader.consumeAscii(HTTP_VERSION)) {
            throw HttpRequestException.NOT_SUPPORTED;
        }

        if (!this.reader.consume(CR, LF)) {
            throw HttpRequestException.MALFORMED;
        }
    }

    private void parseHeaders() {
        this.headersStart = this.reader.position();

        // TODO feed headerStart/headerEnd from ProtocolHandler?

        int nameStart;
        int nameLength;
        int valueStart;
        int valueLength;
        int headerCount = 0;
        while (true) {
            if (headerCount >= MAX_HEADER_COUNT) {
                throw HttpRequestException.TOO_MANY_HEADERS;
            }
            // Empty line => end of headers
            if (this.reader.consume(CR, LF)) {
                this.headersLength = this.reader.position() - this.headersStart;
                this.bodyStart = this.reader.position();
                return;
            }

            nameStart = this.reader.position();
            if (this.reader.matchesAsciiIgnoreCase(HOST)) {
                if (this.host.length() > 0) { // multiple Host headers are not allowed
                    throw HttpRequestException.MALFORMED;
                }
                nameLength = HOST.length - 1;
                this.reader.skip(HOST.length);
                valueStart = this.reader.position();
                valueLength = this.reader.indexOf(CR, LF);
                if (valueLength == -1) {
                    throw HttpRequestException.MALFORMED; // CRLF
                }
                this.host.reset(this.reader, valueStart, valueLength);
                this.host.trim();
                if (!HostValidator.isValidHost(this.host)) {
                    throw HttpRequestException.MALFORMED;
                }
                this.reader.skip(valueLength);
            } else if (this.reader.matchesAsciiIgnoreCase(CONTENT_LENGTH)) {
                nameLength = CONTENT_LENGTH.length - 1;
                this.reader.skip(CONTENT_LENGTH.length);
                valueStart = this.reader.position();
                // parse content length
                int digits = 0;
                int value = 0;
                valueLength = this.reader.indexOf(CR, LF);
                int i;
                for (i = 0; i < valueLength; i++) {
                    final int c = this.reader.getUnsignedByte();
                    if (c >= '0' && c <= '9') {
                        value = value * 10 + (c - '0');
                        digits++;
                    }
                }
                if (digits == 0) {
                    throw HttpRequestException.MALFORMED; // no digit or CRLF
                }
                this.contentLength = value; // predefined header only saved in the contentLength
            } else if (this.reader.matchesAsciiIgnoreCase(TRANSFER_ENCODING)) {
                nameLength = TRANSFER_ENCODING.length - 1;
                this.reader.skip(TRANSFER_ENCODING.length);
                valueStart = this.reader.position();
                valueLength = this.reader.indexOf(CR, LF);
                parseTransferEncoding(valueLength);
            } else { // Unknown/vendor headers
                nameLength = parseHeaderName();
                valueStart = this.reader.position();
                valueLength = this.reader.indexOf(CR, LF);
                if (valueLength < 0) {
                    throw HttpRequestException.MALFORMED;
                }
                this.reader.skip(valueLength); // value can have multi values like: 123, abc, def
            }
            if (!this.reader.consume(CR, LF)) { // Every header must end with CRLF
                throw HttpRequestException.MALFORMED;
            }
            // header values are saved without trimming
            // it is lazily trimmed when requested in getHeader method
            addHeader(headerCount++ << 2, nameStart, nameLength, valueStart, valueLength);
            this.headersCount = headerCount;
        }
    }

    private int parseHeaderName() {
        int nameLength;
        final int offset = this.reader.blockOffset();
        final int available = (int) this.reader.segment().byteSize() - offset;
        final MemorySegment segment = this.reader.segment();
        final int invalid = firstNonTchar(segment, offset, available);
        if (invalid >= 0) {
            final byte value = segment.get(ValueLayout.JAVA_BYTE, offset + invalid);
            if (value != ':') {
                throw HttpRequestException.MALFORMED;
            }
            if (invalid == 0) { // header name must not be empty
                throw HttpRequestException.MALFORMED;
            }
            nameLength = invalid;
            this.reader.skip(nameLength + 1); // +1 for ':'
        } else {
            this.reader.skip(available); // skip the scanned region
            nameLength = this.reader.indexOf(COLON);
            if (nameLength == -1) { // header name must not be empty
                throw HttpRequestException.MALFORMED;
            }
            nameLength += available;
            if (nameLength == 0) { // header name must not be empty
                throw HttpRequestException.MALFORMED;
            }
            if (!checkHeaderNameTokens(nameLength)) {
                throw HttpRequestException.MALFORMED;
            }
            this.reader.skip(1);
        }
        return nameLength;
    }

    private boolean checkHeaderNameTokens(int length) {
        while (length-- > 0 && isTchar(this.reader.get())) {
            // skip
        }
        return length == -1;
    }

    private void skipOWS() {
        while (this.reader.consume(SPACE) || this.reader.consume(HTAB)) {
            // leading OWS
        }
    }

    private int rewindOWS() {
        int rewind = 0;
        while (this.reader.unconsume(SPACE) || this.reader.unconsume(HTAB)) {
            // trailing OWS
            rewind++;
        }
        return rewind;
    }

    private void addHeader(int offset, int nameStart, int nameLength, int valueStart, int valueLength) {
        this.headers[offset] = nameStart;
        this.headers[offset + 1] = nameLength;
        this.headers[offset + 2] = valueStart;
        this.headers[offset + 3] = valueLength;
    }

    private void parseTransferEncoding(int length) {
        if (length < 0) {
            throw HttpRequestException.MALFORMED;
        }
        if (length < TRANSFER_CODE_CHUNKED.length) {
            throw HttpRequestException.MALFORMED;
        }
        this.reader.skip(length);
        final int rewind = rewindOWS();
        this.reader.rewind(TRANSFER_CODE_CHUNKED.length);
        if (!this.reader.matchesAsciiIgnoreCase(TRANSFER_CODE_CHUNKED)) {
            throw HttpRequestException.UNSUPPORTED_TRANSFER_ENCODING;
        }
        this.reader.skip(TRANSFER_CODE_CHUNKED.length + rewind); // keep position aligned
        this.chunked = true;
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
        this.bodyStart = this.reader.position();
        this.bodyLength = this.contentLength;
        this.reader.skip(this.bodyLength);
    }

    private void parseChunkedBody() {
        // TODO not implement yet!
    }

    public HttpMethod getMethod() {
        if (this.method == null) {
            this.method = HttpMethod.of(this.reader.getAscii(this.methodStart, this.methodLength));
        }
        return this.method;
    }

    public ByteString getTarget() {
        return this.target;
    }

    public int getHeadersCount() {
        return this.headersCount;
    }

    public boolean matchHeaderName(int index, String name) {
        final int offset = index << 2;
        return this.reader.matchesAsciiIgnoreCase(name, this.headers[offset], this.headers[offset + 1]);
    }

    public String getHeaderName(int index) {
        final int offset = index << 2;
        return this.reader.getAscii(this.headers[offset], this.headers[offset + 1]);
    }

    public String getHeader(int index) {
        final int offset = index << 2;
        return this.reader.getString(this.headers[offset + 2], this.headers[offset + 3], StandardCharsets.US_ASCII, true);
    }

    public int getContentLength() {
        return this.contentLength;
    }

    public byte[] getBody() {
        if (this.bodyLength == 0) {
            return EMPTY_ARRAY;
        }
        final byte[] result = new byte[this.bodyLength];
        this.reader.copyTo(this.bodyStart, result, 0, this.bodyLength);
        return result;
    }

    public String getBodyAsString() {
        if (this.bodyLength == 0) {
            return "";
        }
        return this.reader.getAscii(this.bodyStart, this.bodyLength);
    }

}
