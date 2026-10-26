package io.javafast

import io.javafast.http.HttpStatus
import io.javafast.test.TestUtils
import spock.lang.Specification

class ErrorMapperSpec extends Specification implements TestUtils {

    def 'error & exception mapper works for custom status handlers' () {
        given:
        create {
            it.routes.get('/hello') { it.result('hello') }
            it.routes.get('/error') { throw new IllegalStateException('Server made a boo boo!') }
            it.routes.error(404) {it.result("status: ${it.statusCode()} ${it.path()} not found!") }
            it.routes.exception(IllegalStateException.class) {e, ctx ->
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Exception thrown: ${e.getMessage()}")
            }
        }

        expect:
        verifyAll(get('/error')) {
            statusCode() == 500
            body() == 'Exception thrown: Server made a boo boo!'
        }
        verifyAll(get('/other')) {
            statusCode() == 404
            body() == 'status: 404 /other not found!'
        }
        verifyAll(get('/hello')) {
            statusCode() == 200
            body() == 'hello'
        }
    }

}
