package io.javafast

import io.javafast.config.JavaFastConfig
import io.javafast.test.TestUtils
import spock.lang.Specification

class BeforeAfterSpec extends Specification implements TestUtils {

    static def setRoutes(JavaFastConfig config) {
        config.routes.get('/root') { it.result('root') }
        config.routes.get('/sub/id/other/stuff') { it.result('id-other-stuff') }
        config.routes.get('/angle/i/see/slashes') { it.result('i-see-slashes') }
    }

    static def setHeaders(JavaFastConfig config) {
        config.routes.before { it.header('X-Always', 'true') }
        config.routes.beforeMatched { it.header('X-Before-Star', 'true') }
        config.routes.beforeMatched('/sub/{p}*') { it.header('X-Before-Sub-Curly', it.pathParam('p')) }
        config.routes.beforeMatched('/angle/<a>') { it.header('X-Before-Angle', it.pathParam('a')) }
        config.routes.afterMatched { it.header('X-After-Star', 'true') }
    }

    def 'beforeMatched and afterMatched work' () {
        given:
        create {
            it.routes.before { it.result('ignored') }
            it.routes.beforeMatched { it.result('before-matched') }
            it.routes.get('/hello') { it.result(it.result() + '-hello') }
            it.routes.afterMatched { it.result(it.result() + '-after-1') }
            it.routes.afterMatched { it.result(it.result() + '-after-2') }
            it.routes.after { it.result(it.result() + '!') }
        }

        expect:
        verifyAll(get('/other')) {
            statusCode() == 404
            body() == 'Endpoint GET /other not found!'
        }
        verifyAll(get('/hello')) {
            statusCode() == 200
            body() == 'before-matched-hello-after-1-after-2!'
        }
    }

    def 'path - perfect match' () {
        given:
        create {
            setRoutes(it)
            setHeaders(it)
        }

        expect:
        verifyAll(get('/root')) {
            statusCode() == 200
            body() == 'root'
            headers().firstValue('X-Always').get() == 'true'
            headers().firstValue('X-Before-Star').get() == 'true'
            headers().firstValue('X-After-Star').get() == 'true'
            headers().firstValue('X-Before-Sub-Curly').isEmpty()
            headers().firstValue('X-Before-Angle').isEmpty()
        }

    }

    def 'path - totally different path leads to 404 without global headers' () {

    }

}
