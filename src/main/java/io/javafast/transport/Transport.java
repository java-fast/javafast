package io.javafast.transport;

public interface Transport {

    void start(TransportConfig config);

    void stop();

    int port();

}
