package io.javafast.transport

import io.javafast.test.TestUtils
import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class TransportConcurrencySpec extends Specification implements TestUtils {

    def 'smoke - concurrent dispatch does not corrupt route match state'() {
        given:
        create {
            it.routes.get('/users/{id}') { ctx ->
                Thread.sleep(2)
                ctx.result(ctx.pathParam('id') as String)
            }
        }
        def requestCount = 400
        def ac = new AsyncConditions(requestCount)
        def pool = Executors.newFixedThreadPool(32)
        def failures = new ConcurrentLinkedQueue<String>()

        when:
        requestCount.times { i ->
            pool.submit(() -> {
                def id = "user-${i}"
                def response = get("/users/${id}")
                ac.evaluate { assert true }
                if (response.statusCode() != 200) {
                    failures.add("status:${response.statusCode()}")
                    return
                }
                if (response.body() != id) {
                    failures.add("expected:${id},actual:${response.body()}")
                }
            })
        }
        ac.await(10) // timeout
        pool.close()

        then:
        failures.isEmpty()

        cleanup:
        if (failures.size() > 0) {
            println("failures => ${failures.size()}")
            println(failures)
        }
    }

}
