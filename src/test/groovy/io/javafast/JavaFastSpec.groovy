package io.javafast


import io.javafast.transport.JdkTestTransport
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class JavaFastSpec extends Specification {

    def 'test hello world' () {
        setup:
        def app = JavaFast.create {
            it.transport.factory(JdkTestTransport::new)
            it.routes.get('/', ctx -> ctx.result('Hello World'))
        }.start(8080)
        def client = HttpClient.newHttpClient()

        // wait server to start

        when:
        def response = client.send(
          HttpRequest.newBuilder(URI.create('http://localhost:8080/')).GET().build(),
          HttpResponse.BodyHandlers.ofString()
        )

        then:
        response.statusCode() == 200
        response.body() == 'Hello World'

        cleanup:
        client.close()
        app.stop()
    }

}
