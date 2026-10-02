package io.javafast.transport;

public interface RequestDispatcher {

    void dispatch(TransportBuffer.Reader reader) throws Exception;

}
