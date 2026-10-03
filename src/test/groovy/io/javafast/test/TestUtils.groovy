package io.javafast.test

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpContext
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpPrincipal
import io.javafast.JavaFast
import io.javafast.config.JavaFastConfig
import io.javafast.http.Context
import io.javafast.http.HttpMethod
import io.javafast.internal.RequestContext
import io.javafast.transport.JdkTestTransport
import io.javafast.transport.Transport

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
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

    static class TestExchange extends HttpExchange {

        private final String method
        private final String uri
        int statusCode = -1
        boolean closed = false
        Headers requestHeaders = new Headers()
        InputStream request
        ByteArrayOutputStream response = new ByteArrayOutputStream()

        TestExchange(String method, String uri, String request = null) {
            this.method = method
            this.uri = uri
            if (request == null) {
                this.request = InputStream.nullInputStream()
            } else {
                this.request = new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8))
            }
        }

        @Override
        Headers getRequestHeaders() {
            return this.requestHeaders
        }

        @Override
        Headers getResponseHeaders() {
            return null
        }

        @Override
        URI getRequestURI() {
            return new URI(this.uri)
        }

        @Override
        String getRequestMethod() {
            return method
        }

        @Override
        HttpContext getHttpContext() {
            return null
        }

        @Override
        void close() {
            this.closed = true
        }

        @Override
        InputStream getRequestBody() {
            return this.request
        }

        @Override
        OutputStream getResponseBody() {
            return response
        }

        @Override
        void sendResponseHeaders(int rCode, long responseLength) throws IOException {

        }

        @Override
        InetSocketAddress getRemoteAddress() {
            return null
        }

        @Override
        int getResponseCode() {
            return this.statusCode
        }

        @Override
        InetSocketAddress getLocalAddress() {
            return null
        }

        @Override
        String getProtocol() {
            return null
        }

        @Override
        Object getAttribute(String name) {
            return null
        }

        @Override
        void setAttribute(String name, Object value) {

        }

        @Override
        void setStreams(InputStream i, OutputStream o) {

        }

        @Override
        HttpPrincipal getPrincipal() {
            return null
        }

        String responseText() {
            return response.toString("UTF-8")
        }

    }

    TestExchange exchange(String method, String uri, String request = null) {
        return new TestExchange(method, uri, request)
    }

    Context contextOf(HttpMethod method, String uri) {
        def ctx = new RequestContext();
        ctx.method = method
        ctx.path = uri
        return ctx
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

    HttpResponse<String> post(String url, String body, int port = 8000) {
        return client().send(
          HttpRequest.newBuilder(URI.create("http://localhost:${port}${url}")).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
          HttpResponse.BodyHandlers.ofString()
        )
    }

}
