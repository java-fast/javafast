package io.javafast.transport;

public interface RequestDispatcher {

    void dispatch(TransportBuffer.Writer writer) throws Exception;

}
