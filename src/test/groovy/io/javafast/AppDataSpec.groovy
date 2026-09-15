package io.javafast

import io.javafast.config.KeyAlreadyExistsException
import io.javafast.http.HttpStatus
import io.javafast.transport.JdkTestTransport
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class AppDataSpec extends Specification {

    def 'register throws if key already exists' () {
        when:
        JavaFast.create {
            it.transport.factory(JdkTestTransport::new)
            it.appData('answer', 42)
            it.appData('answer', 43)
        }

        then:
        thrown(KeyAlreadyExistsException)
    }

    def 'data can be accessed through the app' () {
        setup:
        def app = JavaFast.create {
            it.transport.factory(JdkTestTransport::new)
            it.appData('answer', 42)
            it.routes.get('/', ctx -> ctx.result('ok'))
        }.start(8000)
        def client = HttpClient.newHttpClient()

        when:
        def response = client.send(
            HttpRequest.newBuilder(URI.create('http://localhost:8000/')).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )

        then:
        response.statusCode() == 200
        app.appData('answer') == 42

        cleanup:
        client.close()
        app.stop()
    }

    def 'data can be accessed through the Context' () {
        setup:
        def app = JavaFast.create {
            it.transport.factory(JdkTestTransport::new)
            it.appData('answer', 42)
            it.routes.get('/', ctx -> ctx.result(ctx.appData('answer').toString()))
        }.start(8000)
        def client = HttpClient.newHttpClient()

        when:
        def response = client.send(
            HttpRequest.newBuilder(URI.create('http://localhost:8000/')).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )

        then:
        response.statusCode() == 200
        response.body() == '42'

        cleanup:
        client.close()
        app.stop()
    }

    def 'Context#appData() throws if data does not exist' () {
        setup:
        def app = JavaFast.create {
            it.transport.factory(JdkTestTransport::new)
            it.routes.get('/', ctx -> ctx.result(ctx.appData('missing-key').toString()))
        }.start(8000)
        def client = HttpClient.newHttpClient()

        when:
        def response = client.send(
            HttpRequest.newBuilder(URI.create('http://localhost:8000/')).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )

        then:
        response.statusCode() == 500
        response.body() == HttpStatus.INTERNAL_SERVER_ERROR.message()

        cleanup:
        client.close()
        app.stop()
    }

}
