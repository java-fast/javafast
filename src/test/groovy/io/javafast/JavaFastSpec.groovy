package io.javafast

import spock.lang.Specification

class JavaFastSpec extends Specification {

    def 'test hello world' () {
        def app = JavaFast.create {
            it.routes.get('/', ctx -> ctx.result('Hello World'))
        }.start(8080)

        expect:
        app != null

        cleanup:
        app.stop()
    }

}
