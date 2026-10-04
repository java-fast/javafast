package io.javafast.internal;

import io.javafast.transport.TransportBuffer;

/**
 * An immutable sequence of bytes representing a string-like structure. This class provides
 * methods for handling and interpreting byte data as an ASCII-compatible string.
 * <p>
 * Instances of this class are immutable and are backed by a {@link TransportBuffer.Reader}.
 * It is optimized for scenarios where string-like data is represented as raw byte arrays.
 */
public final class ByteString {

    private TransportBuffer.Reader reader;
    private int position;
    private int length;

    private String value; // lazy cache

    public void reset(TransportBuffer.Reader reader, int position, int length) {
        this.reader = reader;
        this.position = position;
        this.length = length;
        this.value = null;
    }

    public int length() {
        return this.length;
    }

    public byte byteAt(int index) {
        checkIndex(index);
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
        this.reader.position(this.position + toffset);
        if (ignoreCase) {
            return this.reader.matchesAsciiIgnoreCase(ascii, ooffset, len);
        }
        return this.reader.matchesAscii(ascii, ooffset, len);
    }

    public String substring(int beginIndex, int endIndex) {
        checkRange(beginIndex, endIndex);
        return this.reader.getAscii(this.position + beginIndex, endIndex - beginIndex);
    }

    public boolean endsWith(String suffix) {
        final int suffixLength = suffix.length();
        if (suffixLength > this.length) {
            return false;
        }
        final int offset = this.length - suffixLength;
        return regionMatches(false, offset, suffix, 0, suffixLength);
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
            this.value = this.reader.getAscii(this.position, this.length);
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
