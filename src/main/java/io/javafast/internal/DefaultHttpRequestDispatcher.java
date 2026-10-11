package io.javafast.internal;

import io.javafast.config.AppData;
import io.javafast.http.HttpStatus;
import io.javafast.transport.RequestDispatcher;
import io.javafast.transport.TransportBuffer;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default http request dispatcher
 *
 * <p>Transport adapters can use this executor as their RequestDispatcher implementation.
 * It creates a fresh RequestContext per request, executes the router pipeline, and flushes the response.</p>
 */
public final class DefaultHttpRequestDispatcher implements RequestDispatcher {

    private static final Logger log = Logger.getLogger(DefaultHttpRequestDispatcher.class.getName());

    private final RequestContextPool pool;
    private final DefaultRouting routing;
    private final AppData appData;

    public DefaultHttpRequestDispatcher(RequestContextPool pool, DefaultRouting routing, AppData appData) {
        this.pool = pool;
        this.routing = routing;
        this.appData = appData;
    }

    @Override
    public void dispatch(TransportBuffer.Writer writer) throws Exception {
        final RequestContext context = this.pool.get();
        context.reset(writer, this.appData);
        try {
            this.routing.handle(context);
        } catch (Throwable throwable) {
            if (!this.routing.handleException(context, throwable)) {
                context.status(HttpStatus.INTERNAL_SERVER_ERROR).result(HttpStatus.INTERNAL_SERVER_ERROR.message());
                log.log(Level.SEVERE, throwable.getMessage(), throwable);
            }
            this.routing.handleError(context.statusCode(), context);
        } finally {
            context.writeResponse();
            this.pool.release(context);
        }
    }
}
