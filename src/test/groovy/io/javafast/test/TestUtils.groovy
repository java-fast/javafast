package io.javafast.test

import io.javafast.JavaFast
import io.javafast.config.JavaFastConfig
import io.javafast.http.Context
import io.javafast.http.HttpMethod
import io.javafast.internal.ContextInternal
import io.javafast.http.HttpExchange
import io.javafast.transport.JdkTestTransport
import io.javafast.transport.Transport

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.function.Consumer
import java.util.function.Supplier

trait TestUtils {

    static PrintStream originalOut

    static {
        println("TEST PROPERTIES ->")
        System.properties
          .findAll {k, v -> k.toString().startsWith('test.')}
          .each {k, v -> println("\t${k} = ${v}")}
    }

    static HttpClient client

    def setupSpec() {
        boolean logEnabled = Boolean.parseBoolean(System.getProperty("test.logger", "true"))
        if (!logEnabled) {
            originalOut = System.out
            System.setOut(new PrintStream(OutputStream.nullOutputStream()))
        }
    }

    def cleanupSpec() {
        if (originalOut != null) {
            System.setOut(originalOut)
        }
        if(client != null) {
            println("Closing HTTP client: ${client}")
            client.close()
        }
    }

    static class TestExchange implements HttpExchange {

        private final String method
        private final String uri
        int statusCode = -1
        boolean closed = false
        ByteArrayOutputStream response = new ByteArrayOutputStream()

        TestExchange(String method, String uri) {
            this.method = method
            this.uri = uri
        }

        @Override
        String method() {
            return method
        }

        @Override
        String uri() {
            return uri
        }

        @Override
        String header(String name) {
            return null
        }

        @Override
        InputStream requestBody() {
            return InputStream.nullInputStream()
        }

        @Override
        void status(int status) {
            this.statusCode = status
        }

        @Override
        void header(String name, String value) {
        }

        @Override
        OutputStream responseBody() {
            return response
        }

        @Override
        void close() {
            this.closed = true
        }

        String responseText() {
            return response.toString("UTF-8")
        }

    }

    TestExchange exchange(String method, String uri) {
        return new TestExchange(method, uri)
    }

    Context contextOf(HttpMethod method, String uri) {
        return new ContextInternal(null, new TestExchange(method.name(), uri))
    }

    JavaFast create(Consumer<JavaFastConfig> config, Supplier<Transport> transportFactory = JdkTestTransport::new, int port = 8000) {
        return JavaFast.create {
            it.transport.factory(transportFactory)
            config.accept(it)
        }.start(port)
    }

    HttpClient client() {
        try {
            return (client = (client == null) ? HttpClient.newHttpClient() : client)
        } finally {
            println("Using HTTP client: ${client}")
        }
    }

    HttpResponse<String> get(String url, int port = 8000) {
        return client().send(
          HttpRequest.newBuilder(URI.create("http://localhost:${port}${url}")).GET().build(),
          HttpResponse.BodyHandlers.ofString()
        )
    }

}
