package io.javafast.internal;

import io.javafast.transport.TransportBuffer;

import java.nio.charset.StandardCharsets;

/**
 * An immutable sequence of bytes representing a string-like structure. This class provides
 * methods for handling and interpreting byte data as an ASCII-compatible string.
 * <p>
 * Instances of this class are immutable and are backed by a {@link TransportBuffer.Reader}.
 * It is optimized for scenarios where string-like data is represented as raw byte arrays.
 */
public final class ByteString {

    private static final byte SPACE = ' ';
    private static final byte HTAB = '\t';

    private TransportBuffer.Reader reader;
    private int position; // reader position (absolute)
    private int offset; // bytes array offset
    private int length;

    private String value; // lazy cache
    private final byte[] bytes; // value scratch buffer

    public ByteString(int bufferSize) {
        this.bytes = new byte[bufferSize];
    }

    public void clear() {
        this.length = 0;
    }

    public void reset(TransportBuffer.Reader reader, int position, int length) {
        this.reader = reader;
        this.position = position;
        this.offset = 0;
        this.length = length;
        this.value = null;
        if (this.length <= this.bytes.length) {
            reader.copyTo(this.position, this.bytes, this.offset, this.length);
        }
    }

    public int length() {
        return this.length;
    }

    public byte byteAt(int index) {
        checkIndex(index);
        if (this.length <= this.bytes.length) {
            return this.bytes[this.offset + index];
        }
        return this.reader.getAt(this.position + index);
    }

    /**
     * Check region matches with another ASCII string
     *
     * @param ascii must be ASCII (char < 128)
     */
    public boolean regionMatches(boolean ignoreCase, int toffset, String ascii, int ooffset, int len) {
        if (toffset < 0
            || ooffset < 0
            || len < 0
            || toffset > this.length - len
            || ooffset > ascii.length() - len) {
            return false;
        }

        if (this.length <= this.bytes.length) { // for small strings, compare bytes directly
            for (int i = 0; i < len; i++) {
                final byte actual = this.bytes[this.offset + toffset + i];
                final byte expected = (byte) ascii.charAt(ooffset + i);
                if (actual != expected &&
                    (!ignoreCase || (actual | 0x20) != (expected | 0x20))) {
                    return false;
                }
            }
            return true;
        }

        final int pos = this.reader.position();
        try {
            this.reader.position(this.position + toffset);
            if (ignoreCase) {
                return this.reader.matchesAsciiIgnoreCase(ascii, ooffset, len);
            }
            return this.reader.matchesAscii(ascii, ooffset, len);
        } finally {
            this.reader.position(pos); // restore position
        }
    }

    public int indexOf(char c) {
        if (this.length <= this.bytes.length) { // for small strings, compare bytes directly
            for (int i = 0; i < this.length; i++) {
                final byte actual = this.bytes[this.offset + i];
                final byte expected = (byte) c;
                if (actual == expected) {
                    return i;
                }
            }
            return -1;
        }
        final int pos = this.reader.position();
        try {
            this.reader.position(this.position);
            return this.reader.indexOf((byte) c);
        } finally {
            this.reader.position(pos);
        }
    }

    public String substring(int beginIndex, int endIndex) {
        checkRange(beginIndex, endIndex);
        if (this.length <= this.bytes.length) {
            return new String(this.bytes, this.offset + beginIndex, endIndex - beginIndex, StandardCharsets.US_ASCII);
        }
        return this.reader.getAscii(this.position + beginIndex, endIndex - beginIndex);
    }

    public void trim() {
        if (this.length <= this.bytes.length) {
            int pos = 0;
            while (pos < this.length && (this.bytes[pos] == SPACE || this.bytes[pos] == HTAB)) { // leading OWS
                this.offset++;
                this.position++;
                pos++;
            }
            pos = this.length - 1;
            while (pos >= 0 && (this.bytes[pos] == SPACE || this.bytes[pos] == HTAB)) { // trailing OWS
                this.length--;
                pos--;
            }
            this.length -= this.offset;
            return;
        }
        // trim via reader, do not modify cursor
        final int pos = this.reader.position();
        try {
            int trimStart = 0;
            this.reader.position(this.position);
            while (trimStart < this.length && (this.reader.consume(SPACE) || this.reader.consume(HTAB))) { // leading OWS
                this.position++;
                trimStart++;
            }
            int trimEnd = this.length;
            this.reader.skip(trimEnd - trimStart);
            while (trimEnd > 0 && (this.reader.unconsume(SPACE) || this.reader.unconsume(HTAB))) { // trailing OWS
                this.length--;
                trimEnd--;
            }
            this.length -= trimStart;
        } finally {
            this.reader.position(pos);
        }
    }

    public boolean endsWith(char c) {
        if (1 > this.length) {
            return false;
        }
        if (this.length <= this.bytes.length) {
            return this.bytes[this.offset + this.length - 1] == c;
        }
        return this.reader.getAt(this.position + this.length - 1) == c;
    }

    public boolean contentEquals(char other) {
        if (1 != this.length) {
            return false;
        }
        if (this.length <= this.bytes.length) {
            return this.bytes[this.offset] == other;
        }
        return this.reader.getAt(this.position) == other;
    }

    public boolean contentEquals(String other) {
        if (other == null || other.length() != this.length) {
            return false;
        }
        return regionMatches(false, 0, other, 0, this.length);
    }

    @Override
    public String toString() {
        if (this.value == null) {
            if (this.length <= this.bytes.length) {
                this.value = new String(this.bytes, this.offset, this.length, StandardCharsets.US_ASCII);
            } else {
                this.value = this.reader.getAscii(this.position, this.length);
            }
        }
        return this.value;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= this.length) {
            throw new StringIndexOutOfBoundsException(index);
        }
    }

    private void checkRange(int beginIndex, int endIndex) {
        if (beginIndex < 0 || endIndex > this.length || beginIndex > endIndex) {
            throw new StringIndexOutOfBoundsException("begin " + beginIndex + ", end " + endIndex);
        }
    }
}
