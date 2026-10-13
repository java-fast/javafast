package io.javafast.http;

import java.util.Map;

/**
 * TODO also design with no stacktrace?
 * Exception used by handlers to terminate processing with a specific HTTP response.
 */
public class HttpResponseException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> details;

    public HttpResponseException(HttpStatus status) {
        this(status, status.toString(), Map.of());
    }

    public HttpResponseException(HttpStatus status, String message) {
        this(status, message, Map.of());
    }

    public HttpResponseException(HttpStatus status, String message, Map<String, String> details) {
        super(message);
        this.status = status;
        this.details = Map.copyOf(details); // TODO why do we need to copy the map?
    }

    public HttpStatus status() {
        return this.status;
    }

    public Map<String, String> details() {
        return this.details;
    }

    public static HttpResponseException of(HttpStatus status) {
        return new HttpResponseException(status);
    }

    public static HttpResponseException of(HttpStatus status, String message) {
        return new HttpResponseException(status, message);
    }

    public static HttpResponseException notFound(HttpMethod method, String path) {
        return new HttpResponseException(HttpStatus.NOT_FOUND, "Endpoint %s %s not found".formatted(method, path));
    }
}
