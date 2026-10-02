package io.javafast.transport;

public interface ProtocolHandler {

    void onBytes(TransportBuffer.Reader reader, int bytes);

    boolean payloadComplete();

    void reset();

}
