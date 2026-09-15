package io.javafast

import io.javafast.config.KeyAlreadyExistsException
import io.javafast.http.HttpStatus
import io.javafast.test.TestUtils
import io.javafast.transport.JdkTestTransport
import spock.lang.Specification

class AppDataSpec extends Specification implements TestUtils {

    JavaFast app

    def cleanup() {
        if(app != null) app.stop()
    }

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
        given:
        app = create {
            it.appData('answer', 42)
            it.routes.get('/', ctx -> ctx.result('ok'))
        }

        expect:
        get('/').statusCode() == 200
        app.appData('answer') == 42
    }

    def 'data can be accessed through the Context' () {
        given:
        app = create {
            it.appData('answer', 42)
            it.routes.get('/', ctx -> ctx.result(ctx.appData('answer').toString()))
        }

        expect:
        verifyAll(get('/')) {
            statusCode() == 200
            body() == '42'
        }
    }

    def 'Context#appData() throws if data does not exist' () {
        given:
        app = create {
            it.routes.get('/', ctx -> ctx.result(ctx.appData('missing-key').toString()))
        }

        expect:
        verifyAll(get('/')) {
            statusCode() == 500
            body() == HttpStatus.INTERNAL_SERVER_ERROR.message()
        }
    }

}
