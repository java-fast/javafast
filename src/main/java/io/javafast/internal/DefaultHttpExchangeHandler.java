package io.javafast.internal;

import io.javafast.config.AppData;
import io.javafast.http.HttpStatus;
import io.javafast.transport.ExchangeHandler;
import io.javafast.http.HttpExchange;

/**
 * Default http exhcange handler
 *
 * <p>Transport adapters can use this executor as their ExchangeHandler implementation.
 * It creates a fresh ContextInternal per request, executes the router pipeline, and flushes the response.</p>
 */
public final class DefaultHttpExchangeHandler implements ExchangeHandler<HttpExchange> {

    private final DefaultRouting routing;
    private final AppData appData;

    public DefaultHttpExchangeHandler(DefaultRouting routing, AppData appData) {
        this.routing = routing;
        this.appData = appData;
    }

    @Override
    public void handle(HttpExchange exchange) throws Exception {
        final ContextInternal context = new ContextInternal(this.appData, exchange);
        try {
            this.routing.handle(context);
        } catch (Throwable throwable) {
            if (!this.routing.handleException(context, throwable)) {
                context.status(HttpStatus.INTERNAL_SERVER_ERROR).result(HttpStatus.INTERNAL_SERVER_ERROR.message());
            }
            this.routing.handleError(context.statusCode(), context);
        }
        context.writeResponseToExchange();
        exchange.close();
    }
}
