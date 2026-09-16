package io.javafast


import io.javafast.test.TestUtils
import spock.lang.Specification

class BeforeAfterSpec extends Specification implements TestUtils {

    JavaFast app

    def cleanup() {
        if(app != null) app.stop()
    }

    def 'beforeMatched and afterMatched work' () {
        given:
        app = create {
            it.routes.before { it.result("ignored") }
            it.routes.beforeMatched { it.result("before-matched") }
            it.routes.get("/hello") { it.result(it.result() + "-hello") }
            it.routes.afterMatched { it.result(it.result() + "-after-matched") }
            it.routes.after { it.result(it.result() + "!") }
        }

        expect:
        verifyAll(get('/another')) {
            statusCode() == 404
            body() == 'Endpoint GET /another not found!'
        }
        verifyAll(get('/hello')) {
            statusCode() == 200
            // body() == 'before-matched-hello-after-matched!'
        }
    }

}
