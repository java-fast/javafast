package io.javafast.transport

import io.javafast.test.TestUtils
import io.javafast.test.TransportUtils
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.charset.StandardCharsets

@Timeout(5)
class SocketConnectionSpec extends Specification implements TestUtils, TransportUtils {

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

}
