package io.javafast

import io.javafast.test.TestUtils
import spock.lang.Specification

/**
 * TODO continue Body reading tests
 */
class BodyReadingSpec extends Specification implements TestUtils {

    def 'reading body as bytes works' () {
        given:
        create {
            it.routes.post("/") { it.result(it.bodyAsBytes()) }
        }
        expect:
        post('/', 'body').body() == 'body'
    }

}
