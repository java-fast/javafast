package io.javafast.transport

import io.javafast.test.TransportUtils
import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class TransportBufferConcurrencySpec extends Specification implements TransportUtils {

    def 'should allow a consumer to claim a request after another consumer releases it'() {
        given:
        def buffer = TransportBuffer.create(64, 2)
        def payloadA = bytes(101, 64)
        def payloadB = bytes(202, 96)
        def sourceA = transportSource(payloadA, [7, 9, 16, 32])
        def sourceB = transportSource(payloadB, [8, 8, 16, 32, 32])

        when:
        def first = buffer.acquire()
        sourceA.readAll(buffer, first)
        first.release()
        def second = buffer.acquire()
        sourceB.readAll(buffer, second)

        then:
        first != null
        second != null
        first == second
        readBytes(buffer, second, payloadB.length) == payloadB

        when:
        second.release()

        then:
        buffer.freeBlockCount() == 2

        cleanup:
        buffer.close()
    }

    def 'should allow multiple producers to write concurrently'() {
        given:
        def buffer = TransportBuffer.create(64, 64)
        def producerCount = 24
        def payloadLength = 28
        def ac = new AsyncConditions(producerCount)
        def pool = Executors.newFixedThreadPool(12)

        when: 'write concurrently'
        producerCount.times { i ->
            pool.submit {
                def head = buffer.acquire()
                def source = transportSource(bytes(i, payloadLength), [9, 9, 10])
                if (source.readAll(buffer, head) == payloadLength) {
                    head.release()
                    ac.evaluate { assert true }
                }
            }
        }
        ac.await(10)
        pool.close()

        then: 'all blocks are released'
        buffer.freeBlockCount() == 64

        cleanup:
        buffer.close()
    }

    def 'should preserve all requests when multiple producers write concurrently'() {
        given:
        def buffer = TransportBuffer.create(64, 64)
        def producerCount = 20
        def payloadLength = 28
        def payloads = (0..<producerCount).collect { bytes(1000 + it, payloadLength) }
        def ac = new AsyncConditions(producerCount)
        def pool = Executors.newFixedThreadPool(10)
        def blocks = new CopyOnWriteArrayList<TransportBuffer.Block>()

        when:
        payloads.each { byte[] data ->
            pool.submit {
                def source = transportSource(data, [7, 11, 10])
                def head = buffer.acquire()
                ac.evaluate {
                    assert source.readAll(buffer, head) == payloadLength
                }
                blocks.add(head)
            }
        }
        ac.await(10)
        pool.close()

        then:
        blocks.size() == producerCount
        blocks.collect { requestKey(readBytes(buffer, it, payloadLength)) }.toSet() == payloads.collect { requestKey(it) }.toSet()

        cleanup:
        buffer.close()
    }

    def 'should never assign the same block to multiple producers'() {
        given:
        def buffer = TransportBuffer.create(64, 32)
        def producerCount = 8
        def payloadLength = 96
        def ac = new AsyncConditions(producerCount)
        def pool = Executors.newFixedThreadPool(producerCount)
        def heads = new CopyOnWriteArrayList<TransportBuffer.Block>()

        when:
        producerCount.times { i ->
            pool.submit {
                def source = transportSource(bytes(2000 + i, payloadLength), [13, 17, 19, 23])
                def head = buffer.acquire()
                ac.evaluate {
                    assert source.readAll(buffer, head) == payloadLength
                }
                heads.add(head)
            }
        }
        ac.await(10)
        pool.close()
        def allBlocks = heads.collectMany { chain(buffer, it) }

        then:
        allBlocks.size() == 2 * producerCount
        allBlocks*.toString().toSet().size() == allBlocks.size()

        cleanup:
        buffer.close()
    }

    def 'should allow concurrent producers to write requests spanning multiple blocks'() {
        given:
        def buffer = TransportBuffer.create(64, 64)
        def producerCount = 10
        def payloadLength = 160
        def ac = new AsyncConditions(producerCount)
        def pool = Executors.newFixedThreadPool(10)
        def heads = new CopyOnWriteArrayList<TransportBuffer.Block>()

        when:
        producerCount.times { i ->
            pool.submit {
                def source = transportSource(bytes(5000 + i, payloadLength), [17, 19, 23, 11])
                def head = buffer.acquire()
                ac.evaluate { assert source.readAll(buffer, head) == payloadLength }
                heads.add(head)
            }
        }
        ac.await(10)
        pool.close()

        then:
        heads.size() == producerCount
        heads.every { chain(buffer, it).size() == 3 }

        cleanup:
        buffer.close()
    }

    def 'should release the complete chain through the head'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payload = bytes(9100, 140)
        def source = transportSource(payload, [17, 19, 23])
        def head = buffer.acquire()
        assert source.readAll(buffer, head) == payload.length

        when:
        def blocks = chain(buffer, head)

        then:
        head != null
        blocks.size() == 3
        readBytes(buffer, head, payload.length) == payload

        when:
        head.release()

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should not reuse a block before it is released'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payloadLength = 20

        def head = null
        (0..<8).each {
            def source = transportSource(bytes(9700 + it, payloadLength), [7, 13])
            head = buffer.acquire()
            assert source.readAll(buffer, head) == payloadLength
        }
        assert head != null

        when: 'extra acquire'
        def block = buffer.acquire()

        then: 'no blocks available'
        block == null

        when: 'one is released'
        head.release()
        block = buffer.acquire()

        then: 'block can be acquired'
        block == head
        buffer.freeBlockCount() == 0

        cleanup:
        buffer.close()
    }

    def 'should not expose stale next links after block reuse'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def first = bytes(10100, 140)
        def second = bytes(10101, 24)
        def firstClaim = buffer.acquire()
        assert firstClaim != null
        assert transportSource(first, [13, 17, 19]).readAll(buffer, firstClaim) == first.length
        firstClaim.release()

        when:
        def secondClaim = buffer.acquire()
        assert transportSource(second, [7, 9, 8]).readAll(buffer, secondClaim) == second.length

        then:
        secondClaim != null
        !hasNext(buffer, secondClaim)
        readBytes(buffer, secondClaim, second.length) == second

        when:
        secondClaim.release()

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should handle concurrent writes across ring wrap-around'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        (0..<6).each {
            def b = bytes(10200 + it, 20)
            def claim = buffer.acquire()
            assert claim != null
            assert transportSource(b, [7, 13]).readAll(buffer, claim)
            claim.release()
        }

        def producerCount = 6
        def payloadLength = 22
        def payloads = (0..<producerCount).collect { bytes(10300 + it, payloadLength) }
        def ac = new AsyncConditions(producerCount)
        def pool = Executors.newFixedThreadPool(producerCount)
        def claims = new CopyOnWriteArrayList<TransportBuffer.Block>()

        when:
        payloads.each { byte[] data ->
            pool.submit {
                def head = buffer.acquire()
                ac.evaluate {
                    assert transportSource(data, [5, 7, 10]).readAll(buffer, head) == payloadLength
                }
                claims.add(head)
            }
        }
        ac.await(10)
        pool.close()

        then:
        claims.size() == producerCount
        claims.collect { requestKey(readBytes(buffer, it, payloadLength)) }.toSet() == payloads.collect { requestKey(it) }.toSet()

        cleanup:
        buffer.close()
    }

    def 'should reuse blocks after producer cursor wraps around'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payloadLength = 20

        when:
        24.times { i ->
            def bytes = bytes(10500 + i, payloadLength)
            def claim = buffer.acquire()
            assert transportSource(bytes, [5, 7, 8]).readAll(buffer, claim) == payloadLength
            assert readBytes(buffer, claim, payloadLength) == bytes
            claim.release()
        }

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

}
