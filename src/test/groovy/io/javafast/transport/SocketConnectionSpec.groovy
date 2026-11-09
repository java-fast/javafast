package io.javafast.transport

import io.javafast.test.TestUtils
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.charset.StandardCharsets

@Timeout(30)
class SocketConnectionSpec extends Specification implements TestUtils {

    private static String readResponse(InputStream input, String expectedBody) {
        def bytes = new ByteArrayOutputStream()
        while (bytes.toString(StandardCharsets.US_ASCII.name()).count(expectedBody) < 1) {
            int value = input.read()
            assert value != -1
            bytes.write(value)
        }
        return bytes.toString(StandardCharsets.US_ASCII.name())
    }

    private static String request(String path, String headers = '', String body = '') {
        "GET ${path} HTTP/1.1\r\nHost: localhost\r\n${headers}\r\n${body}"
    }

    def 'should process two requests sent in a single socket write'() {
        given:
        create(8001, { -> new JdkHttpTransport(TransportBuffer.create(1024, 1024)) }) {
            it.routes.get('/hello') { ctx -> ctx.result('Hello') }
        }

        and:
        def requests = 'GET /hello HTTP/1.1\r\n'
        requests += 'Host: localhost\r\n'
        requests += '\r\n'
        requests += 'GET /hello HTTP/1.1\r\n'
        requests += 'Host: localhost\r\n'
        requests += '\r\n'

        when:
        String response = ''
        new Socket('localhost', 8001).withCloseable { socket ->
            socket.soTimeout = 3000
            socket.outputStream.write(requests.getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            def bytes = new ByteArrayOutputStream()
            while (bytes.toString(StandardCharsets.US_ASCII.name()).count('Hello') < 2) {
                bytes.write(socket.inputStream.read())
            }
            response = bytes.toString(StandardCharsets.US_ASCII.name())
        }

        then:
        response.count('HTTP/1.1 200') == 2
        response.count('Hello') == 2

        cleanup:
        println(response)

    }

    def 'should process headers fragmented over multiple socket writes'() {
        given:
        create(8001, { -> new JdkHttpTransport(TransportBuffer.create(1024, 1024)) }) {
            it.routes.get('/hello') { ctx -> ctx.result('Hello') }
        }

        when:
        String response
        new Socket('localhost', 8001).withCloseable { socket ->
            socket.soTimeout = 3000
            def output = socket.outputStream
            output.write('GET /hello HTTP/1.1\r\nHost:'.getBytes(StandardCharsets.US_ASCII))
            output.flush()
            output.write(' localhost\r\nX-Test: fragmented\r\n'.getBytes(StandardCharsets.US_ASCII))
            output.flush()
            output.write('\r\n'.getBytes(StandardCharsets.US_ASCII))
            output.flush()
            response = readResponse(socket.inputStream, 'Hello')
        }

        then:
        response.contains('HTTP/1.1 200')
        response.contains('Hello')
    }

    def 'should keep parser state isolated when connections are interleaved'() {
        given:
        create(8001, { -> new JdkHttpTransport(TransportBuffer.create(1024, 1024)) }) {
            it.routes.get('/a') { ctx -> ctx.result('A') }
            it.routes.get('/b') { ctx -> ctx.result('B') }
        }

        when:
        String responseA
        String responseB
        new Socket('localhost', 8001).withCloseable { socketA ->
            new Socket('localhost', 8001).withCloseable { socketB ->
                socketA.soTimeout = 3000
                socketB.soTimeout = 3000
                socketA.outputStream.write('GET /a HTTP/1.1\r\nHost: localhost\r\n'.getBytes(StandardCharsets.US_ASCII))
                socketA.outputStream.flush()
                socketB.outputStream.write(request('/b').getBytes(StandardCharsets.US_ASCII))
                socketB.outputStream.flush()

                responseB = readResponse(socketB.inputStream, 'B')
                socketA.outputStream.write('\r\n'.getBytes(StandardCharsets.US_ASCII))
                socketA.outputStream.flush()
                responseA = readResponse(socketA.inputStream, 'A')
            }
        }

        then:
        responseA.contains('HTTP/1.1 200')
        responseA.contains('A')
        responseB.contains('HTTP/1.1 200')
        responseB.contains('B')
    }

    def 'should process a body fragmented over multiple socket writes'() {
        given:
        create(8001, { -> new JdkHttpTransport(TransportBuffer.create(1024, 1024)) }) {
            it.routes.post('/body') { ctx -> ctx.result(ctx.body()) }
        }

        when:
        String response
        new Socket('localhost', 8001).withCloseable { socket ->
            socket.soTimeout = 3000
            def output = socket.outputStream
            output.write('POST /body HTTP/1.1\r\nHost: localhost\r\nContent-Length: 11\r\n\r\nhello'.getBytes(StandardCharsets.US_ASCII))
            output.flush()
            output.write(' world'.getBytes(StandardCharsets.US_ASCII))
            output.flush()
            response = readResponse(socket.inputStream, 'hello world')
        }

        then:
        response.contains('HTTP/1.1 200')
        response.contains('hello world')
    }

    def 'should close an idle socket after its read timeout'() {
        setup:
        create(8001, { -> new JdkHttpTransport(TransportBuffer.create(1024, 1024)) }) { }

        when:
        int readResult = Integer.MIN_VALUE
        new Socket('localhost', 8001).withCloseable { socket ->
            socket.soTimeout = 15_000 // read idle timeout is 10 seconds!
            readResult = socket.inputStream.read()
        }

        then:
        readResult == -1 // should end up here in 10 seconds
    }

}
