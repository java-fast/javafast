package io.javafast.transport;

public record TransportConfig(String host, int port) {

    public static TransportConfig defaults() {
        return new TransportConfig(null, 8080);
    }

}
