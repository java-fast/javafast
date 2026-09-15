package io.javafast

import io.javafast.config.KeyAlreadyExistsException
import io.javafast.http.HttpStatus
import io.javafast.test.TestUtils
import io.javafast.transport.JdkTestTransport
import spock.lang.Specification

class AppDataSpec extends Specification implements TestUtils {

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
        def app = create {
            it.appData('answer', 42)
            it.routes.get('/', ctx -> ctx.result('ok'))
        }

        when:
        def res = get('/')

        then:
        res.statusCode() == 200
        app.appData('answer') == 42

        cleanup:
        app.stop()
    }

    def 'data can be accessed through the Context' () {
        setup:
        def app = create {
            it.appData('answer', 42)
            it.routes.get('/', ctx -> ctx.result(ctx.appData('answer').toString()))
        }

        when:
        def response = get('/')

        then:
        response.statusCode() == 200
        response.body() == '42'

        cleanup:
        app.stop()
    }

    def 'Context#appData() throws if data does not exist' () {
        setup:
        def app = create {
            it.routes.get('/', ctx -> ctx.result(ctx.appData('missing-key').toString()))
        }

        when:
        def response = get('/')

        then:
        response.statusCode() == 500
        response.body() == HttpStatus.INTERNAL_SERVER_ERROR.message()

        cleanup:
        app.stop()
    }

}
