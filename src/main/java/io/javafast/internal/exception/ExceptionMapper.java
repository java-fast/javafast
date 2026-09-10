package io.javafast.internal.exception;

import io.javafast.http.Context;
import io.javafast.http.ExceptionHandler;
import io.javafast.http.HttpResponseException;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal exception-handler registry. No transport-specific exception knowledge lives here.
 */
public final class ExceptionMapper {

    private final Map<Class<? extends Exception>, ExceptionHandler<Exception>> handlers = new HashMap<>();
    private final Map<Class<? extends Exception>, ExceptionHandler<Exception>> resolved = new HashMap<>();

    public ExceptionMapper() {
        add(HttpResponseException.class, (HttpResponseException exception, Context context) -> {
            context.status(exception.status());
            if (exception.getMessage() != null) context.result(exception.getMessage());
        });
    }

    public <E extends Exception> void add(Class<E> type, ExceptionHandler<? super E> handler) {
        this.handlers.put(type, (exception, context) -> handler.handle(type.cast(exception), context));
        this.resolved.clear();
    }

    public boolean handle(Context context, Throwable throwable) {
        if (!(throwable instanceof Exception exception)) return false;

        final var handler = resolve(exception.getClass());
        if (handler == null) return false;
        invoke(handler, exception, context);
        return true;
    }

    private ExceptionHandler<Exception> resolve(Class<? extends Exception> type) {
        final var cached = this.resolved.get(type);
        if (cached != null) return cached;

        Class<?> current = type;
        while (current != null && current != Object.class) {
            final ExceptionHandler<Exception> handler = getExceptionHandler(type, current);
            if (handler != null) return handler;
            for (Class<?> iface : current.getInterfaces()) {
                final ExceptionHandler<Exception> interfaceHandler = getExceptionHandler(type, iface);
                if (interfaceHandler != null) return interfaceHandler;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private ExceptionHandler<Exception> getExceptionHandler(Class<? extends Exception> type, Class<?> current) {
        if (Exception.class.isAssignableFrom(current)) {
            final var exceptionClass = current.asSubclass(Exception.class);
            final var handler = this.handlers.get(exceptionClass);
            if (handler != null) {
                this.resolved.put(type, handler);
                return handler;
            }
        }
        return null;
    }

    private static void invoke(ExceptionHandler<Exception> handler, Exception exception, Context context) {
        handler.handle(exception, context);
    }
}
