package io.javafast.internal;

import io.javafast.http.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal status/content-type error handler registry
 */
final class ErrorMapper {

    private static final int MIN_STATUS = 100;
    private static final int MAX_STATUS = 599;

    private record Handler(String contentType, io.javafast.http.Handler handler) {
    }

    private final List<Handler>[] handlers = new List[MAX_STATUS - MIN_STATUS + 1];

    private List<Handler> get(int status) {
        return this.handlers[status - MIN_STATUS];
    }

    private void set(int status, List<Handler> handlers) {
        this.handlers[status - MIN_STATUS] = handlers;
    }

    void add(int status, String contentType, io.javafast.http.Handler handler) {
        checkStatus(status);
        List<Handler> handlers = get(status);
        if (handlers == null) {
            handlers = new ArrayList<>();
            set(status, handlers);
        }
        handlers.add(new Handler(contentType, handler));
    }

    void handle(int status, Context context) throws Exception {
        if (status < MIN_STATUS || status > MAX_STATUS) {
            return;
        }
        final List<Handler> handlers = get(status);
        if (handlers == null) {
            return;
        }
        final String accept = context.header("Accept");
        for (Handler handler : handlers) {
            if ("*".equals(handler.contentType()) || accepts(accept, handler.contentType())) {
                handler.handler().handle(context);
            }
        }
    }

    private static void checkStatus(int status) {
        if (status < MIN_STATUS || status > MAX_STATUS) {
            throw new IllegalArgumentException("Invalid HTTP status: " + status);
        }
    }

    private static boolean accepts(String accept, String contentType) {
        // TODO possible allocation in toLowerCase
        return accept != null && accept.toLowerCase(java.util.Locale.ROOT)
            .contains(contentType.toLowerCase(java.util.Locale.ROOT));
    }

}
