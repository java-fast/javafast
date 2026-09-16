package io.javafast

import io.javafast.test.TestUtils
import spock.lang.Specification

class JavaFastSpec extends Specification implements TestUtils {

    def 'test hello world' () {
        given:
        create {
            it.routes.get('/', ctx -> ctx.result('Hello World'))
        }

        expect:
        verifyAll(get('/')) {
            statusCode() == 200
            body() == 'Hello World'
        }
    }

    def 'test hello world with contextPath' () {
        given:
        create {
            it.router.contextPath = '/api'
            it.routes.get('/', ctx -> ctx.result('Hello World'))
        }

        expect:
        verifyAll(get('/api/')) {
            statusCode() == 200
            body() == 'Hello World'
        }
    }

}
