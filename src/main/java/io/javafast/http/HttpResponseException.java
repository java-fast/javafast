package io.javafast.http;

/**
 * Exception used by handlers to terminate processing with a specific HTTP response.
 */
public class HttpResponseException extends RuntimeException {

    public static final HttpResponseException NOT_FOUND = new HttpResponseException(HttpStatus.NOT_FOUND);
    public static final HttpResponseException FORBIDDEN = new HttpResponseException(HttpStatus.FORBIDDEN);

    private final HttpStatus status;

    private HttpResponseException(HttpStatus status) {
        this(status, null);
    }

    private HttpResponseException(HttpStatus status, String message) {
        super(message, null, false, false); // no stacktrace, no state
        this.status = status;
    }

    public HttpStatus status() {
        return this.status;
    }

    public static HttpResponseException of(HttpStatus status) {
        return new HttpResponseException(status);
    }

    public static HttpResponseException of(HttpStatus status, String message) {
        return new HttpResponseException(status, message);
    }

}
