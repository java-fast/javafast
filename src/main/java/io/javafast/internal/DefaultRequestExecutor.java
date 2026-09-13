package io.javafast.internal;

import io.javafast.http.HttpStatus;
import io.javafast.transport.ExchangeHandler;
import io.javafast.transport.HttpExchange;

/**
 * TODO public or internal?
 * TODO since they are package-private, how to connect pre-configured router, error and exception mappers?
 * Default internal request executor.
 *
 * <p>Transport adapters can use this executor as their ExchangeHandler implementation.
 * It creates a fresh ContextInternal per request, executes the router pipeline, and flushes the response.</p>
 */
public final class DefaultRequestExecutor implements ExchangeHandler {

    private final Router router;
    private final ErrorMapper errorMapper;
    private final ExceptionMapper exceptionMapper;

    public DefaultRequestExecutor(Router router) {
        this.router = router;
        this.errorMapper = new ErrorMapper();
        this.exceptionMapper = new ExceptionMapper();
    }

    @Override
    public void handle(HttpExchange exchange) throws Exception {
        final ContextInternal context = new ContextInternal(exchange);
        try {
            this.router.handle(context);
        } catch (Throwable throwable) {
            if (!this.exceptionMapper.handle(context, throwable)) {
                context.status(HttpStatus.INTERNAL_SERVER_ERROR).result(HttpStatus.INTERNAL_SERVER_ERROR.message());
            }
            this.errorMapper.handle(context.statusCode(), context);
        }

        context.writeResponseToExchange();
        exchange.close();
    }
}
