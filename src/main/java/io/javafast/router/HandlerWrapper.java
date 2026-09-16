package io.javafast.router;

import io.javafast.http.Handler;

/**
 * Optional handler decorator applied when a route is registered.
 */
public interface HandlerWrapper {

    Handler wrap(Handler handler);

}
