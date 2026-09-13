package io.javafast.transport;

public interface ExchangeHandler<T> {

    void handle(T exchange) throws Exception;

}
