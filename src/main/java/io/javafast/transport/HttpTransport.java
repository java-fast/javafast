package io.javafast.transport;

public interface HttpTransport {

    void start(TransportConfig config, ExchangeHandler handler);

    void stop();
}
