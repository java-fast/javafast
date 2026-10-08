package io.javafast.transport;

public class HttpRequestException extends RuntimeException {

    public static final HttpRequestException NOT_SUPPORTED = new HttpRequestException();
    public static final HttpRequestException MALFORMED = new HttpRequestException();
    public static final HttpRequestException TOO_MANY_HEADERS = new HttpRequestException();
    public static final HttpRequestException UNSUPPORTED_TRANSFER_ENCODING = new HttpRequestException();
    public static final HttpRequestException REQUEST_TOO_LARGE = new HttpRequestException();

    private HttpRequestException() {
        super(null, null, false, false); // no stacktrace, no state
    }

}
