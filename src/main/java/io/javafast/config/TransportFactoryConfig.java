package io.javafast.config;

import io.javafast.transport.Transport;

import java.util.function.Supplier;

public class TransportFactoryConfig {

    private Supplier<Transport> transport;

    public void factory(Supplier<Transport> transport) {
        this.transport = transport;
    }

    public Transport get() {
        return this.transport.get();
    }

}
