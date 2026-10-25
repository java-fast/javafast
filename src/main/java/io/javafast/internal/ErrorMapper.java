package io.javafast.internal;

import io.javafast.http.Context;
import io.javafast.http.Handler;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal status/content-type error handler registry
 */
final class ErrorMapper {

    private static final int MIN_STATUS = 100;
    private static final int MAX_STATUS = 599;

    private final List<Entry>[] handlers = new List[MAX_STATUS + 1];

    void add(int status, String contentType, Handler handler) {
        checkStatus(status);
        List<Entry> entries = this.handlers[status];
        if (entries == null) {
            entries = new ArrayList<>();
            this.handlers[status] = entries;
        }
        entries.add(new Entry(contentType, handler));
    }

    void handle(int status, Context context) throws Exception {
        if (status < MIN_STATUS || status > MAX_STATUS) {
            return;
        }
        final List<Entry> entries = this.handlers[status];
        if (entries == null) {
            return;
        }
        final String accept = context.header("Accept");
        for (Entry entry : entries) {
            if ("*".equals(entry.contentType()) || accepts(accept, entry.contentType())) {
                entry.handler().handle(context);
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

    private record Entry(String contentType, Handler handler) {
    }
}
