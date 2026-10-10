package io.javafast.transport;

public interface RequestDispatcher {

    void dispatch(long sequence, TransportBuffer.Reader reader) throws Exception;

}
