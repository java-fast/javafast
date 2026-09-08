package io.javafast.transport;

public interface ExchangeHandler {

    void handle(HttpExchange exchange) throws Exception;

}
