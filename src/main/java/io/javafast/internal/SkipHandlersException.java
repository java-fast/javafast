package io.javafast.internal;

/**
 * A control-flow exception to cut the Router pipeline when remaining handlers should be skipped.
 */
public final class SkipHandlersException extends RuntimeException {

    private static final SkipHandlersException INSTANCE = new SkipHandlersException();

    private SkipHandlersException() {
        super(null, null, false, false); // no message, no cause, no suppression, no stacktrace
    }

    public static SkipHandlersException get() {
        return INSTANCE;
    }

}
