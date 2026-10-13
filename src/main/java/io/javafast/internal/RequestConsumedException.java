package io.javafast.internal;

/**
 * An exception to indicate the request is read and cannot be read again
 */
final class RequestConsumedException extends RuntimeException {

    private static final RequestConsumedException INSTANCE = new RequestConsumedException();

    private RequestConsumedException() {
        super(null, null, false, false); // no message, no cause, no suppression, no stacktrace
    }

    static RequestConsumedException get() {
        return INSTANCE;
    }

}
