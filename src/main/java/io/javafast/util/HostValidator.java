package io.javafast.util;

import io.javafast.internal.ByteString;

/**
 * Utility class for validating host names.
 * This is not a fully RFC compliant validator but still handles most cases
 */
public class HostValidator {

    private static boolean isValidRegNameChar(int c) {
        return (c >= 'a' && c <= 'z')
            || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9')
            || c == '-'
            || c == '.';
    }

    private static boolean isHexDigit(int c) {
        return (c >= '0' && c <= '9')
            || (c >= 'a' && c <= 'f')
            || (c >= 'A' && c <= 'F');
    }

    private static boolean isValidIpv6Literal(ByteString host, int start, int end) {
        if (start >= end) {
            return false;
        }

        int groups = 0;
        int groupLength = 0;
        int compressionPosition = -1;
        for (int i = start; i < end; i++) {
            final int c = host.byteAt(i) & 0xFF;
            if (c == ':') {
                if (i + 1 < end && host.byteAt(i + 1) == ':') {
                    if (compressionPosition >= 0) {
                        return false;
                    }
                    compressionPosition = groups;
                    i++;
                    groupLength = 0;
                    continue;
                }
                if (groupLength == 0) {
                    return false;
                }
                groups++;
                groupLength = 0;
                continue;
            }
            if (!isHexDigit(c) || ++groupLength > 4) {
                return false;
            }
        }

        if (groupLength > 0) {
            groups++;
        } else if (host.byteAt(end - 1) != ':') {
            return false;
        }

        return compressionPosition >= 0 ? groups < 8 : groups == 8;
    }

    public static boolean isValidHost(ByteString host) {
        final int length = host.length();
        if (length == 0) return false;

        int portStart = -1;
        if (host.byteAt(0) == '[') { // IPv6 literal: [::1] or [::1]:8080
            int closingBracket = -1;
            for (int i = 1; i < length; i++) {
                if (host.byteAt(i) == ']') {
                    closingBracket = i;
                    break;
                }
            }
            if (closingBracket < 0 || closingBracket == 1) {
                return false;
            }
            // IPv6 parsing must validate the literal itself.
            if (!HostValidator.isValidIpv6Literal(host, 1, closingBracket - 1)) {
                return false;
            }
            if (closingBracket + 1 == length) {
                return true;
            }
            if (host.byteAt(closingBracket + 1) != ':') {
                return false;
            }
            portStart = closingBracket + 2;
        } else { // IPv4 or domain name
            int colon = -1;
            for (int i = 0; i < length; i++) {
                final int c = host.byteAt(i) & 0xFF;
                if (c == ':') {
                    if (colon >= 0) {
                        return false; // multiple ports
                    }
                    colon = i;
                    continue;
                }
                if (!HostValidator.isValidRegNameChar(c)) {
                    return false;
                }
            }
            if (colon >= 0) {
                portStart = colon + 1;
                if (colon == 0) {
                    return false;
                }
            }
        }

        if (portStart >= 0) {
            if (portStart >= length) {
                return false; // empty port
            }
            int port = 0;
            for (int i = portStart; i < length; i++) {
                final int c = host.byteAt(i) & 0xFF;
                if (c < '0' || c > '9') {
                    return false;
                }
                port = port * 10 + (c - '0');
                if (port > 65535) {
                    return false;
                }
            }
        }

        return true;
    }

}
