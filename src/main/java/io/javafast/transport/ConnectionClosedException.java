package io.javafast.transport;

import java.io.IOException;

class ConnectionClosedException extends IOException {

    private static final ConnectionClosedException INSTANCE = new ConnectionClosedException();

    private ConnectionClosedException() {
    }

    private ConnectionClosedException(Throwable cause) {
        super(cause);
    }

    static ConnectionClosedException get() {
        return INSTANCE;
    }

    static ConnectionClosedException get(Throwable cause) {
        return new ConnectionClosedException(cause);
    }

}
