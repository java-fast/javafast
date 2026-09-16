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

trait TestUtils implements Logger {

    static HttpClient client // shared in spec level
    JavaFast app // shared in method level

    def setupSpec() {
        printMethodStart()
    }

    def cleanupSpec() {
        if(client != null) {
            log("Closing HTTP client: ${client}")
            client.close()
        }
        printMethodEnd()
    }

    def cleanup() {
        if(app != null) app.stop()
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

    JavaFast create(int port, Supplier<Transport> transportFactory, Consumer<JavaFastConfig> config) {
        return app = JavaFast.create {
            it.transport.factory(transportFactory)
            config.accept(it)
        }.start(port)
    }

    JavaFast create(Consumer<JavaFastConfig> config) {
        return create(8000, JdkTestTransport::new, config)
    }

    HttpClient client() {
        if (client == null) {
            client = HttpClient.newHttpClient()
            log("Using HTTP client: ${client}")
        }
        return client
    }

    HttpResponse<String> get(String url, int port = 8000) {
        return client().send(
          HttpRequest.newBuilder(URI.create("http://localhost:${port}${url}")).GET().build(),
          HttpResponse.BodyHandlers.ofString()
        )
    }

    HttpResponse<Object> head(String url, int port = 8000) {
        return client().send(
          HttpRequest.newBuilder(URI.create("http://localhost:${port}${url}")).HEAD().build(),
          HttpResponse.BodyHandlers.discarding()
        )
    }

}
