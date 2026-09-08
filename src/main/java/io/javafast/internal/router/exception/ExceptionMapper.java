package io.javafast.internal.router.exception;

import io.javafast.http.Context;
import io.javafast.http.ExceptionHandler;
import io.javafast.http.HttpResponseException;

import java.util.HashMap;
import java.util.Map;

/** Internal exception-handler registry. No transport-specific exception knowledge lives here. */
public final class ExceptionMapper {
    private final Map<Class<? extends Exception>, ExceptionHandler<?>> handlers = new HashMap<>();
    private final Map<Class<? extends Exception>, ExceptionHandler<?>> resolved = new HashMap<>();

    public ExceptionMapper() {
        add(HttpResponseException.class, (HttpResponseException exception, Context context) -> {
            context.status(exception.status());
            if (exception.getMessage() != null) context.result(exception.getMessage());
        });
    }

    public <E extends Exception> void add(Class<E> type, ExceptionHandler<? super E> handler) {
        handlers.put(type, handler);
        resolved.clear();
    }

    public boolean handle(Context context, Throwable throwable) throws Exception {
        if (!(throwable instanceof Exception exception)) return false;

        ExceptionHandler<?> handler = resolve(exception.getClass());
        if (handler == null) return false;
        invoke(handler, exception, context);
        return true;
    }

    private ExceptionHandler<?> resolve(Class<? extends Exception> type) {
        ExceptionHandler<?> cached = resolved.get(type);
        if (cached != null) return cached;

        Class<?> current = type;
        while (current != null && current != Object.class) {
            @SuppressWarnings("unchecked")
            ExceptionHandler<?> handler = handlers.get((Class<? extends Exception>) current);
            if (handler != null) {
                resolved.put(type, handler);
                return handler;
            }
            for (Class<?> iface : current.getInterfaces()) {
                @SuppressWarnings("unchecked")
                ExceptionHandler<?> interfaceHandler = handlers.get((Class<? extends Exception>) iface);
                if (interfaceHandler != null) {
                    resolved.put(type, interfaceHandler);
                    return interfaceHandler;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void invoke(ExceptionHandler handler, Exception exception, Context context) {
        handler.handle(exception, context);
    }
}
