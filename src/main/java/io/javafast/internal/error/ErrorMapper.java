package io.javafast.internal.error;

import io.javafast.http.Context;
import io.javafast.http.Handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal status/content-type error handler registry.
 */
public final class ErrorMapper {

    private final Map<Integer, List<Entry>> handlers = new HashMap<>();

    public void add(int status, String contentType, Handler handler) {
        this.handlers.computeIfAbsent(status, ignored -> new ArrayList<>())
            .add(new Entry(contentType, handler));
    }

    public void handle(int status, Context context) throws Exception {
        final List<Entry> entries = this.handlers.get(status);
        if (entries == null) return;

        final String accept = context.header("Accept");
        for (Entry entry : entries) {
            if ("*".equals(entry.contentType) || accepts(accept, entry.contentType)) {
                entry.handler.handle(context);
            }
        }
    }

    private static boolean accepts(String accept, String contentType) {
        return accept != null && accept.toLowerCase(java.util.Locale.ROOT)
            .contains(contentType.toLowerCase(java.util.Locale.ROOT));
    }

    private record Entry(String contentType, Handler handler) {
    }
}
