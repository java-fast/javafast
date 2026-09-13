package io.javafast.test

import io.javafast.http.Context
import io.javafast.http.HttpMethod
import io.javafast.internal.ContextInternal
import io.javafast.http.HttpExchange

class TestUtils {

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

    static TestExchange exchange(String method, String uri) {
        return new TestExchange(method, uri)
    }

    static Context contextOf(HttpMethod method, String uri) {
        return new ContextInternal(new TestExchange(method.name(), uri))
    }

}
