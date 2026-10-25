package io.javafast.http;

public class HttpRequestException extends RuntimeException {

    public static final HttpRequestException NOT_SUPPORTED = new HttpRequestException(501);
    public static final HttpRequestException MALFORMED = new HttpRequestException(400);
    public static final HttpRequestException TOO_MANY_HEADERS = new HttpRequestException(431);
    public static final HttpRequestException UNSUPPORTED_TRANSFER_ENCODING = new HttpRequestException(501);
    public static final HttpRequestException REQUEST_TOO_LARGE = new HttpRequestException(413);

    private final int status;

    private HttpRequestException(int status) {
        super(null, null, false, false); // no stacktrace, no state
        this.status = status;
    }

    public int status() {
        return this.status;
    }

}
