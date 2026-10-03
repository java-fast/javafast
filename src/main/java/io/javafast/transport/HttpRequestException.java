package io.javafast.transport;

public class HttpRequestException extends RuntimeException {

    public HttpRequestException(String message) {
        super(message);
    }

    public static HttpRequestException notSupported() {
        return new HttpRequestException("HTTP Version is not supported");
    }

    public static HttpRequestException malformed() {
        return new HttpRequestException("Malformed request");
    }

    public static HttpRequestException tooManyHeaders() {
        return new HttpRequestException("Too many headers");
    }

    public static HttpRequestException unsupportedTransferEncoding() {
        return new HttpRequestException("Unsupported transfer encoding");
    }

    public static HttpRequestException requestTooLarge() {
        return new HttpRequestException("Request too large");
    }
}
