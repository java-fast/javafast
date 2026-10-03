package io.javafast.transport;

/**
 * Protocol handler for HTTP request and response.
 * The only responsibility is to read the request to detect payload completion.
 * It is crucial for a ProtocolHandler not to try a full-parsing of the request.
 */
public interface ProtocolHandler {

    /**
     * Read available bytes from the reader.
     * @param reader current reader
     * @param bytes ready to read byte count
     */
    void onBytes(TransportBuffer.Reader reader, int bytes);

    boolean payloadComplete();

    void reset();

}
