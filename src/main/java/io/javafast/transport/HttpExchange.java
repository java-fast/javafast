package io.javafast.transport;

import java.io.InputStream;
import java.io.OutputStream;

public interface HttpExchange {

    String method();

    String uri();

    String header(String name);

    InputStream requestBody();

    void status(int status);

    void header(String name, String value);

    OutputStream responseBody();

    void close();
}
