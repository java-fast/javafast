package io.javafast.transport;

import java.io.IOException;

public class ConnectionClosedException extends IOException {

    public ConnectionClosedException() {
    }

    public ConnectionClosedException(Throwable cause) {
        super(cause);
    }

}
