package io.javafast

import io.javafast.config.JavaFastConfig
import io.javafast.http.HttpStatus
import io.javafast.test.TestUtils
import spock.lang.Specification

class BeforeAfterSpec extends Specification implements TestUtils {

    static def setRoutes(JavaFastConfig config) {
        config.routes.get('/root') { it.result('root') }
        config.routes.get('/sub/id/other/stuff') { it.result('id-other-stuff') }
        config.routes.get('/angle/i/see/things') { it.result('i-see-things') }
    }

    static def setHeaders(JavaFastConfig config) {
        config.routes.before { it.header('X-Always', 'true') }
        config.routes.beforeMatched { it.header('X-Before-Star', 'true') }
        config.routes.beforeMatched('/sub/{p}*') { it.header('X-Before-Sub-Curly', it.pathParam('p')) }
        config.routes.beforeMatched('/angle/<a>') { it.header('X-Before-Angle', it.pathParam('a')) } // angle brackets should capture path with slashes!
        config.routes.afterMatched { it.header('X-After-Star', 'true') }
    }

    def 'beforeMatched and afterMatched work' () {
        given:
        create {
            it.routes.before { it.result('ignored') } // should be ignored
            it.routes.beforeMatched { it.result('before-matched') } // "ignored" is overwritten
            it.routes.get('/hello') { it.result(it.result() + '-hello') }
            it.routes.afterMatched { it.result(it.result() + '-after-1') }
            it.routes.afterMatched { it.result(it.result() + '-after-2') } // multiple phase handlers should work in order
            it.routes.after { it.result(it.result() + '!') } // should run after even in an exception case!
        }

        expect:
        verifyAll(get('/other')) {
            statusCode() == 404
            body() == '!'
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
        given:
        create {
            setRoutes(it)
            setHeaders(it)
        }

        expect:
        verifyAll(get('/other')) {
            statusCode() == 404
            body() == ''
            headers().firstValue('X-Always').get() == 'true'
            headers().firstValue('X-Before-Star').isEmpty()
            headers().firstValue('X-After-Star').isEmpty()
            headers().firstValue('X-Before-Sub-Curly').isEmpty()
            headers().firstValue('X-Before-Angle').isEmpty()
        }
    }

    def 'path - matching sub curly in beforeMatched but no matching http handler leads to 404' () {
        given:
        create {
            setRoutes(it)
            setHeaders(it)
        }

        expect:
        verifyAll(get('/sub/id/fake')) {
            statusCode() == 404
            body() == ''
            headers().firstValue('X-Always').get() == 'true'
            headers().firstValue('X-Before-Star').isEmpty()
            headers().firstValue('X-After-Star').isEmpty()
            headers().firstValue('X-Before-Sub-Curly').isEmpty()
            headers().firstValue('X-Before-Angle').isEmpty()
        }
    }

    def 'path - matching angle brackets in beforeMatched but no matching http handler leads to 404' () {
        given:
        create {
            setRoutes(it)
            setHeaders(it)
        }

        expect:
        verifyAll(get('/angle/i/see/nothing')) {
            statusCode() == 404
            body() == ''
            headers().firstValue('X-Always').get() == 'true'
            headers().firstValue('X-Before-Star').isEmpty()
            headers().firstValue('X-After-Star').isEmpty()
            headers().firstValue('X-Before-Sub-Curly').isEmpty()
            headers().firstValue('X-Before-Angle').isEmpty()
        }
    }

    def 'path - sub curly in before matched happy path' () {
        given:
        create {
            setRoutes(it)
            setHeaders(it)
        }

        expect:
        verifyAll(get('/sub/id/other/stuff')) {
            statusCode() == 200
            body() == 'id-other-stuff'
            headers().firstValue('X-Always').get() == 'true'
            headers().firstValue('X-Before-Star').get() == 'true'
            headers().firstValue('X-After-Star').get() == 'true'
            headers().firstValue('X-Before-Sub-Curly').get() == 'id'
            headers().firstValue('X-Before-Angle').isEmpty()
        }
    }

    def 'path - angle brackets in beforeMatched happy path' () {
        given:
        create {
            setRoutes(it)
            setHeaders(it)
        }

        expect:
        verifyAll(get('/angle/i/see/things')) {
            statusCode() == 200
            body() == 'i-see-things'
            headers().firstValue('X-Always').get() == 'true'
            headers().firstValue('X-Before-Star').get() == 'true'
            headers().firstValue('X-After-Star').get() == 'true'
            headers().firstValue('X-Before-Sub-Curly').isEmpty()
            headers().firstValue('X-Before-Angle').get() == 'i/see/things'
        }
    }

    def 'beforeMatched can skip remaining handlers' () {
        given:
        create {
            it.routes.beforeMatched {
                it.result('skipped')
                it.skipRemainingHandlers()
            }
            it.routes.get('/hello') {it.result('hello') }
        }

        expect:
        verifyAll(get('/other-path')) {
            statusCode() == 404
            body() == ''
        }
        verifyAll(get('/hello')) {
            statusCode() == 200
            body() == 'skipped'
        }
    }

    def 'beforeMatched fires for head request on get handler' () {
        given:
        create {
            it.routes.beforeMatched { it.status(HttpStatus.IM_A_TEAPOT) }
            it.routes.get('/hello') {it.result('hello') }
            it.routes.afterMatched { it.result(it.result() + '!') }
        }

        expect:
        head('/hello').statusCode() == HttpStatus.IM_A_TEAPOT.code()
        get('/hello').statusCode() == HttpStatus.IM_A_TEAPOT.code()
        get('/hello').body() == 'hello!'
        get('/other').statusCode() == HttpStatus.NOT_FOUND.code()
    }

    def 'beforeMatched without its own path-params inherits the matched route\'s pathParams' () {
        given:
        create {
            it.routes.beforeMatched { it.result(it.pathParamMap().toString() + "/") }
            it.routes.get("/{endpoint}") { ctx -> ctx.result(ctx.result() + ctx.pathParamMap()) }
        }

        expect:
        get('/param').body() == '[endpoint:param]/[endpoint:param]'
    }

    def 'pathParams are extracted from beforeMatched if beforeMatched has path-params' () {
        given:
        create {
            it.routes.beforeMatched('/{before}') { it.result(it.pathParamMap().toString()) }
            it.routes.get("/{endpoint}") { ctx -> ctx.result(ctx.result() + '/' + ctx.pathParamMap()) }
        }

        expect:
        get('/param').body() == '[before:param]/[endpoint:param]'
    }

    def 'global beforeMatched sees pathParamMap from matched endpoint' () {
        given:
        create {
            it.routes.beforeMatched { ctx -> ctx.result(ctx.pathParamMap().toString()) }
            it.routes.get("/api/user/{userId}") { }
        }
        expect:
        get('/api/user/user-999').body() == '[userId:user-999]'
    }

    def 'global beforeMatched can read individual pathParam from matched endpoint' () {
        given:
        create {
            it.routes.beforeMatched { ctx -> ctx.result(ctx.pathParam('userId')) }
            it.routes.get("/api/user/{userId}") { }
        }
        expect:
        get('/api/user/user-999').body() == 'user-999'
    }

    def 'afterMatched without its own path-params inherits the matched route\'s pathParams' () {
        given:
        create {
            it.routes.afterMatched { it.result(it.result() + '/' + it.pathParamMap().toString()) }
            it.routes.get("/{endpoint}") { it.result(it.pathParamMap().toString()) }
        }

        expect:
        get('/param').body() == '[endpoint:param]/[endpoint:param]'
    }
}
