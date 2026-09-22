package io.javafast.transport

import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

import java.lang.foreign.MemorySegment
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TransferBufferConcurrencySpec extends Specification {

    def 'should allow multiple consumers to claim different requests concurrently'() {
        given:
        def buffer = TransportBuffer.create(64, 32)
        def payloadLength = 40
        def payloads = (0..<8).collect { payload(it, payloadLength) }
        payloads.each { assert buffer.write(sourceFromBytes(it, [9, 11, 13])) }

        def consumerCount = 12
        def ac = new AsyncConditions(consumerCount)
        def claims = new ConcurrentLinkedQueue<TransportBuffer.Block>()
        def pool = Executors.newFixedThreadPool(consumerCount)

        when:
        consumerCount.times {
            pool.submit {
                def claim = buffer.claim()
                if (claim != null) {
                    claims.add(claim)
                }
                ac.evaluate { assert true }
            }
        }
        ac.await(10)
        pool.close()

        then:
        claims.size() == payloads.size()
        claims.collect { requestKey(readBytes(it, payloadLength)) }.toSet() == payloads.collect { requestKey(it) }.toSet()
        claims.each { buffer.release(it) }
        buffer.freeBlockCount() == 32

        cleanup:
        buffer.close()
    }

    def 'should never allow two consumers to claim the same request'() {
        given:
        def buffer = TransportBuffer.create(64, 16)
        def payload = bytes(48)
        assert buffer.write(sourceFromBytes(payload, [9, 11, 13, 15]))

        def consumerCount = 24
        def ac = new AsyncConditions(consumerCount)
        def claims = new ConcurrentLinkedQueue<TransportBuffer.Block>()
        def pool = Executors.newFixedThreadPool(consumerCount)

        when:
        consumerCount.times {
            pool.submit {
                def claim = buffer.claim()
                if (claim != null) {
                    claims.add(claim)
                }
                ac.evaluate { assert true }
            }
        }
        ac.await(10)
        pool.close()

        then:
        claims.size() == 1
        readBytes(claims.peek(), payload.length) == payload
        buffer.claim() == null

        when:
        buffer.release(claims.peek())

        then:
        buffer.freeBlockCount() == 16

        cleanup:
        buffer.close()
    }

    def 'should allow a consumer to claim a request after another consumer releases it'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payloadA = payload(101, 32)
        def payloadB = payload(202, 32)
        assert buffer.write(sourceFromBytes(payloadA, [7, 9, 16]))
        assert buffer.write(sourceFromBytes(payloadB, [8, 8, 16]))

        when:
        def firstClaim = buffer.claim()
        buffer.release(firstClaim)
        def secondClaim = buffer.claim()

        then:
        firstClaim != null
        secondClaim != null
        readBytes(secondClaim, payloadB.length) == payloadB

        when:
        buffer.release(secondClaim)

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should not allow consumers to claim an unpublished request'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payload = payload(303, 120)
        def eofReached = new AtomicBoolean(false)
        def allowFinish = new AtomicBoolean(false)
        def writerDone = new AsyncConditions(1)
        def pool = Executors.newFixedThreadPool(16)

        when:
        pool.submit {
            assert buffer.write(blockingEofSource(payload, [11, 13, 17, 19], eofReached, allowFinish))
            writerDone.evaluate { assert true }
        }
        awaitUntil(eofReached)

        def ac = new AsyncConditions(12)
        def claimsBeforePublish = new ConcurrentLinkedQueue<TransportBuffer.Block>()
        12.times {
            pool.submit {
                def claim = buffer.claim()
                if (claim != null) {
                    claimsBeforePublish.add(claim)
                }
                ac.evaluate { assert true }
            }
        }
        ac.await(10)

        then:
        claimsBeforePublish.isEmpty()

        when:
        allowFinish.set(true)
        writerDone.await(10)
        def publishedClaim = awaitClaim(buffer)

        then:
        publishedClaim != null
        readBytes(publishedClaim, payload.length) == payload

        when:
        buffer.release(publishedClaim)

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        pool.close()
        buffer.close()
    }

    def 'should allow multiple producers to write concurrently'() {
        given:
        def buffer = TransportBuffer.create(64, 64)
        def producerCount = 24
        def payloadLength = 28
        def ac = new AsyncConditions(producerCount)
        def pool = Executors.newFixedThreadPool(12)
        def writes = new AtomicInteger(0)

        when:
        producerCount.times { i ->
            pool.submit {
                if (buffer.write(sourceFromBytes(payload(i, payloadLength), [9, 9, 10]))) {
                    writes.incrementAndGet()
                }
                ac.evaluate { assert true }
            }
        }
        ac.await(10)
        pool.close()

        then:
        writes.get() == producerCount
        drainClaims(buffer).size() == producerCount

        cleanup:
        buffer.close()
    }

    def 'should preserve all requests when multiple producers write concurrently'() {
        given:
        def buffer = TransportBuffer.create(64, 64)
        def producerCount = 20
        def payloadLength = 28
        def payloads = (0..<producerCount).collect { payload(1000 + it, payloadLength) }
        def ac = new AsyncConditions(producerCount)
        def pool = Executors.newFixedThreadPool(10)

        when:
        payloads.each { byte[] data ->
            pool.submit {
                assert buffer.write(sourceFromBytes(data, [7, 11, 10]))
                ac.evaluate { assert true }
            }
        }
        ac.await(10)
        pool.close()
        def claims = drainClaims(buffer)

        then:
        claims.size() == producerCount
        claims.collect { requestKey(readBytes(it, payloadLength)) }.toSet() == payloads.collect { requestKey(it) }.toSet()

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

        when:
        producerCount.times { i ->
            pool.submit {
                assert buffer.write(sourceFromBytes(payload(2000 + i, payloadLength), [13, 17, 19, 23]))
                ac.evaluate { assert true }
            }
        }
        ac.await(10)
        pool.close()
        def claims = drainClaims(buffer)
        def allBlocks = claims.collectMany { chain(it) }

        then:
        claims.size() == producerCount
        allBlocks*.toString().toSet().size() == allBlocks.size()

        cleanup:
        buffer.close()
    }

    def 'should not lose requests during concurrent writes'() {
        given:
        def buffer = TransportBuffer.create(64, 128)
        def producerCount = 48
        def payloadLength = 24
        def ac = new AsyncConditions(producerCount)
        def pool = Executors.newFixedThreadPool(16)

        when:
        producerCount.times { i ->
            pool.submit {
                assert buffer.write(sourceFromBytes(payload(3000 + i, payloadLength), [5, 7, 12]))
                ac.evaluate { assert true }
            }
        }
        ac.await(10)
        pool.close()

        then:
        drainClaims(buffer).size() == producerCount

        cleanup:
        buffer.close()
    }

    def 'should not corrupt request chains during concurrent writes'() {
        given:
        def buffer = TransportBuffer.create(64, 64)
        def producerCount = 12
        def payloadLength = 130
        def payloads = (0..<producerCount).collect { payload(4000 + it, payloadLength) }
        def ac = new AsyncConditions(producerCount)
        def pool = Executors.newFixedThreadPool(8)

        when:
        payloads.each { byte[] data ->
            pool.submit {
                assert buffer.write(sourceFromBytes(data, [9, 11, 7, 13, 5]))
                ac.evaluate { assert true }
            }
        }
        ac.await(10)
        pool.close()
        def claims = drainClaims(buffer)

        then:
        claims.size() == producerCount
        claims.collect { requestKey(readBytes(it, payloadLength)) }.toSet() == payloads.collect { requestKey(it) }.toSet()

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

        when:
        producerCount.times { i ->
            pool.submit {
                assert buffer.write(sourceFromBytes(payload(5000 + i, payloadLength), [17, 19, 23, 11]))
                ac.evaluate { assert true }
            }
        }
        ac.await(10)
        pool.close()
        def claims = drainClaims(buffer)

        then:
        claims.size() == producerCount
        claims.every { chain(it).size() > 1 }

        cleanup:
        buffer.close()
    }

    def 'should allow concurrent writes and claims'() {
        given:
        def buffer = TransportBuffer.create(64, 32)
        def totalRequests = 80
        def payloadLength = 30
        def producerThreads = 4
        def consumerThreads = 4
        def ac = new AsyncConditions(producerThreads + consumerThreads)
        def pool = Executors.newFixedThreadPool(producerThreads + consumerThreads)
        def produced = new AtomicInteger(0)
        def consumed = ConcurrentHashMap.newKeySet()

        when:
        producerThreads.times {
            pool.submit {
                while (true) {
                    def id = produced.getAndIncrement()
                    if (id >= totalRequests) {
                        break
                    }
                    def bytes = payload(6000 + id, payloadLength)
                    while (!buffer.write(sourceFromBytes(bytes, [7, 9, 14]))) {
                        Thread.sleep(1)
                    }
                }
                ac.evaluate { assert true }
            }
        }
        consumerThreads.times {
            pool.submit {
                while (consumed.size() < totalRequests) {
                    def claim = buffer.claim()
                    if (claim == null) {
                        Thread.sleep(1)
                        continue
                    }
                    consumed.add(requestKey(readBytes(claim, payloadLength)))
                    buffer.release(claim)
                }
                ac.evaluate { assert true }
            }
        }
        ac.await(20)
        pool.close()

        then:
        consumed.size() == totalRequests
        buffer.freeBlockCount() == 32

        cleanup:
        buffer.close()
    }

    def 'should preserve requests while producers and consumers operate concurrently'() {
        given:
        def buffer = TransportBuffer.create(64, 32)
        def totalRequests = 40
        def payloadLength = 120
        def producerThreads = 4
        def consumerThreads = 4
        def ac = new AsyncConditions(producerThreads + consumerThreads)
        def pool = Executors.newFixedThreadPool(producerThreads + consumerThreads)
        def produced = new AtomicInteger(0)
        Set<String> consumed = ConcurrentHashMap.newKeySet()
        Set<String> expected = (0..<totalRequests).collect { requestKey(payload(7000 + it, payloadLength)) }.toSet()

        when:
        producerThreads.times {
            pool.submit {
                while (true) {
                    def id = produced.getAndIncrement()
                    if (id >= totalRequests) {
                        break
                    }
                    def bytes = payload(7000 + id, payloadLength)
                    while (!buffer.write(sourceFromBytes(bytes, [13, 17, 19, 23]))) {
                        Thread.sleep(1)
                    }
                }
                ac.evaluate { assert true }
            }
        }
        consumerThreads.times {
            pool.submit {
                while (consumed.size() < totalRequests) {
                    def claim = buffer.claim()
                    if (claim == null) {
                        Thread.sleep(1)
                        continue
                    }
                    consumed.add(requestKey(readBytes(claim, payloadLength)))
                    buffer.release(claim)
                }
                ac.evaluate { assert true }
            }
        }
        ac.await(20)
        pool.close()

        then:
        consumed == expected
        buffer.freeBlockCount() == 32

        cleanup:
        buffer.close()
    }

    def 'should not claim a request before it is fully published'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payload = payload(8000, 128)
        def eofReached = new AtomicBoolean(false)
        def allowFinish = new AtomicBoolean(false)
        def pool = Executors.newFixedThreadPool(4)
        def writerDone = new AsyncConditions(1)
        def earlyClaims = new ConcurrentLinkedQueue<TransportBuffer.Block>()

        when:
        pool.submit {
            assert buffer.write(blockingEofSource(payload, [11, 13, 17], eofReached, allowFinish))
            writerDone.evaluate { assert true }
        }
        awaitUntil(eofReached)

        def ac = new AsyncConditions(3)
        3.times {
            pool.submit {
                def c = buffer.claim()
                if (c != null) {
                    earlyClaims.add(c)
                }
                ac.evaluate { assert true }
            }
        }
        ac.await(10)

        then:
        earlyClaims.isEmpty()

        when:
        allowFinish.set(true)
        writerDone.await(10)
        def claim = awaitClaim(buffer)

        then:
        claim != null
        readBytes(claim, payload.length) == payload

        when:
        buffer.release(claim)

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        pool.close()
        buffer.close()
    }

    def 'should not expose partially written requests'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payload = payload(8100, 150)
        def observed = new ConcurrentLinkedQueue<byte[]>()
        def pool = Executors.newFixedThreadPool(2)
        def ac = new AsyncConditions(2)

        when:
        pool.submit {
            assert buffer.write(delayedSource(payload, [7, 9, 11, 13, 17], 2))
            ac.evaluate { assert true }
        }
        pool.submit {
            def claim = awaitClaim(buffer)
            observed.add(readBytes(claim, payload.length))
            buffer.release(claim)
            ac.evaluate { assert true }
        }
        ac.await(15)
        pool.close()

        then:
        observed.size() == 1
        observed.peek() == payload
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should allow blocks to be reused while other requests are being consumed'() {
        given:
        def buffer = TransportBuffer.create(64, 16)
        def heldPayload = payload(8200, 180)
        def smallPayloadLength = 20
        def smallCount = 8
        assert buffer.write(sourceFromBytes(heldPayload, [17, 19, 23]))
        def heldClaim = buffer.claim()
        assert heldClaim != null

        def ac = new AsyncConditions(2)
        def pool = Executors.newFixedThreadPool(2)
        Set<String> consumedSmall = ConcurrentHashMap.newKeySet()
        Set<String> expectedSmall = (0..<smallCount).collect { requestKey(payload(8300 + it, smallPayloadLength)) }.toSet()

        when:
        pool.submit {
            smallCount.times { i ->
                def bytes = payload(8300 + i, smallPayloadLength)
                while (!buffer.write(sourceFromBytes(bytes, [5, 7, 8]))) {
                    Thread.sleep(1)
                }
            }
            ac.evaluate { assert true }
        }
        pool.submit {
            while (consumedSmall.size() < smallCount) {
                def claim = buffer.claim()
                if (claim == null) {
                    Thread.sleep(1)
                    continue
                }
                consumedSmall.add(requestKey(readBytes(claim, smallPayloadLength)))
                buffer.release(claim)
            }
            ac.evaluate { assert true }
        }
        ac.await(15)
        pool.close()
        buffer.release(heldClaim)

        then:
        consumedSmall == expectedSmall
        buffer.freeBlockCount() == 16

        cleanup:
        buffer.close()
    }

    def 'should continue accepting requests when another request is being consumed'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def first = payload(8400, 120)
        def second = payload(8401, 24)
        assert buffer.write(sourceFromBytes(first, [11, 13, 17]))

        when:
        def firstClaim = buffer.claim()
        def secondWrite = buffer.write(sourceFromBytes(second, [7, 8, 9]))
        def secondClaim = awaitClaim(buffer)

        then:
        firstClaim != null
        secondWrite
        secondClaim != null
        readBytes(secondClaim, second.length) == second

        when:
        buffer.release(secondClaim)
        buffer.release(firstClaim)

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should preserve a multi-block request under concurrent access'() {
        given:
        def buffer = TransportBuffer.create(64, 16)
        def payload = payload(9000, 170)
        assert buffer.write(sourceFromBytes(payload, [9, 11, 13, 15]))

        def consumerCount = 10
        def ac = new AsyncConditions(consumerCount)
        def claims = new ConcurrentLinkedQueue<TransportBuffer.Block>()
        def pool = Executors.newFixedThreadPool(consumerCount)

        when:
        consumerCount.times {
            pool.submit {
                def claim = buffer.claim()
                if (claim != null) {
                    claims.add(claim)
                }
                ac.evaluate { assert true }
            }
        }
        ac.await(10)
        pool.close()

        then:
        claims.size() == 1
        readBytes(claims.peek(), payload.length) == payload

        when:
        buffer.release(claims.peek())

        then:
        buffer.freeBlockCount() == 16

        cleanup:
        buffer.close()
    }

    def 'should claim the complete chain through the head'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payload = payload(9100, 140)
        assert buffer.write(sourceFromBytes(payload, [17, 19, 23]))

        when:
        def claim = buffer.claim()
        def blocks = chain(claim)

        then:
        claim != null
        blocks.size() > 1
        readBytes(claim, payload.length) == payload

        when:
        buffer.release(claim)

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should not allow a continuation block to be claimed independently'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payload = payload(9200, 130)
        assert buffer.write(sourceFromBytes(payload, [9, 11, 13]))

        when:
        def claim = buffer.claim()
        def secondClaim = buffer.claim()

        then:
        claim != null
        chain(claim).size() > 1
        secondClaim == null

        when:
        buffer.release(claim)

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should release all blocks in a multi-block request'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payload = payload(9300, 135)
        assert buffer.write(sourceFromBytes(payload, [13, 17, 19]))
        def claim = buffer.claim()
        assert claim != null

        when:
        buffer.release(claim)

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should make all blocks reusable after releasing a multi-block request'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def first = payload(9400, 140)
        def second = payload(9401, 140)
        assert buffer.write(sourceFromBytes(first, [11, 13, 17]))
        def firstClaim = buffer.claim()
        assert firstClaim != null
        buffer.release(firstClaim)

        when:
        def secondWrite = buffer.write(sourceFromBytes(second, [11, 13, 17]))
        def secondClaim = buffer.claim()

        then:
        secondWrite
        secondClaim != null
        readBytes(secondClaim, second.length) == second

        when:
        buffer.release(secondClaim)

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should preserve chain links while the request is being consumed'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payload = payload(9500, 145)
        assert buffer.write(sourceFromBytes(payload, [17, 11, 19]))
        def claim = buffer.claim()
        assert claim != null

        def ac = new AsyncConditions(6)
        def pool = Executors.newFixedThreadPool(6)
        def competingClaims = new ConcurrentLinkedQueue<TransportBuffer.Block>()

        when:
        6.times {
            pool.submit {
                def c = buffer.claim()
                if (c != null) {
                    competingClaims.add(c)
                }
                ac.evaluate { assert true }
            }
        }
        def traversed = chain(claim)
        ac.await(10)
        pool.close()

        then:
        traversed.size() > 1
        readBytes(claim, payload.length) == payload
        competingClaims.isEmpty()

        when:
        buffer.release(claim)

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should reuse released blocks under concurrent writes'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def totalRequests = 20
        def payloadLength = 24
        def producerThreads = 4
        def consumerThreads = 2
        def ac = new AsyncConditions(producerThreads + consumerThreads)
        def pool = Executors.newFixedThreadPool(producerThreads + consumerThreads)
        def produced = new AtomicInteger(0)
        Set<String> consumed = ConcurrentHashMap.newKeySet()
        Set<String> expected = (0..<totalRequests).collect { requestKey(payload(9600 + it, payloadLength)) }.toSet()

        when:
        producerThreads.times {
            pool.submit {
                while (true) {
                    def id = produced.getAndIncrement()
                    if (id >= totalRequests) {
                        break
                    }
                    def bytes = payload(9600 + id, payloadLength)
                    while (!buffer.write(sourceFromBytes(bytes, [5, 7, 12]))) {
                        Thread.sleep(1)
                    }
                }
                ac.evaluate { assert true }
            }
        }
        consumerThreads.times {
            pool.submit {
                while (consumed.size() < totalRequests) {
                    def claim = buffer.claim()
                    if (claim == null) {
                        Thread.sleep(1)
                        continue
                    }
                    consumed.add(requestKey(readBytes(claim, payloadLength)))
                    buffer.release(claim)
                }
                ac.evaluate { assert true }
            }
        }
        ac.await(20)
        pool.close()

        then:
        consumed == expected
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should not reuse a block before it is released'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payloadLength = 20
        (0..<8).each { assert buffer.write(sourceFromBytes(payload(9700 + it, payloadLength), [7, 13])) }
        def claim = buffer.claim()
        assert claim != null

        when:
        def extraWrite = buffer.write(sourceFromBytes(payload(9800, payloadLength), [5, 15]))

        then:
        !extraWrite

        when:
        buffer.release(claim)
        drainClaims(buffer).each { buffer.release(it) }

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should allow a released head block to be claimed by a subsequent request'() {
        given:
        def buffer = TransportBuffer.create(64, 4)
        def first = payload(9900, 22)
        def second = payload(9901, 22)
        assert buffer.write(sourceFromBytes(first, [7, 8]))

        when:
        def firstClaim = buffer.claim()
        buffer.release(firstClaim)
        assert buffer.write(sourceFromBytes(second, [9, 13]))
        def secondClaim = buffer.claim()

        then:
        firstClaim != null
        secondClaim != null
        readBytes(secondClaim, second.length) == second

        when:
        buffer.release(secondClaim)

        then:
        buffer.freeBlockCount() == 4

        cleanup:
        buffer.close()
    }

    def 'should safely reuse continuation blocks after request completion'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def first = payload(10000, 150)
        def second = payload(10001, 150)
        assert buffer.write(sourceFromBytes(first, [11, 13, 17]))
        def firstClaim = buffer.claim()
        assert firstClaim != null
        buffer.release(firstClaim)

        when:
        assert buffer.write(sourceFromBytes(second, [19, 17, 13]))
        def secondClaim = buffer.claim()

        then:
        secondClaim != null
        chain(secondClaim).size() > 1
        readBytes(secondClaim, second.length) == second

        when:
        buffer.release(secondClaim)

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should not expose stale next links after block reuse'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def first = payload(10100, 140)
        def second = payload(10101, 24)
        assert buffer.write(sourceFromBytes(first, [13, 17, 19]))
        def firstClaim = buffer.claim()
        assert firstClaim != null
        buffer.release(firstClaim)

        when:
        assert buffer.write(sourceFromBytes(second, [7, 9, 8]))
        def secondClaim = buffer.claim()

        then:
        secondClaim != null
        secondClaim.next() == null
        readBytes(secondClaim, second.length) == second

        when:
        buffer.release(secondClaim)

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should handle concurrent writes across ring wrap-around'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        (0..<6).each {
            def b = payload(10200 + it, 20)
            assert buffer.write(sourceFromBytes(b, [7, 13]))
            def claim = buffer.claim()
            assert claim != null
            buffer.release(claim)
        }

        def producerCount = 6
        def payloadLength = 22
        def payloads = (0..<producerCount).collect { payload(10300 + it, payloadLength) }
        def ac = new AsyncConditions(producerCount)
        def pool = Executors.newFixedThreadPool(producerCount)

        when:
        payloads.each { byte[] data ->
            pool.submit {
                assert buffer.write(sourceFromBytes(data, [5, 7, 10]))
                ac.evaluate { assert true }
            }
        }
        ac.await(10)
        pool.close()
        def claims = drainClaims(buffer)

        then:
        claims.size() == producerCount
        claims.collect { requestKey(readBytes(it, payloadLength)) }.toSet() == payloads.collect { requestKey(it) }.toSet()

        cleanup:
        buffer.close()
    }

    def 'should handle concurrent claims across ring wrap-around'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payloadLength = 20
        def allPayloads = (0..<14).collect { payload(10400 + it, payloadLength) }
        allPayloads.take(8).each { assert buffer.write(sourceFromBytes(it, [7, 13])) }
        6.times {
            def claim = buffer.claim()
            assert claim != null
            buffer.release(claim)
        }
        allPayloads.drop(8).each { assert buffer.write(sourceFromBytes(it, [7, 13])) }

        def expected = allPayloads.drop(6).collect { requestKey(it) }.toSet()
        def consumerCount = 12
        def ac = new AsyncConditions(consumerCount)
        def pool = Executors.newFixedThreadPool(consumerCount)
        def claims = new ConcurrentLinkedQueue<TransportBuffer.Block>()

        when:
        consumerCount.times {
            pool.submit {
                def claim = buffer.claim()
                if (claim != null) {
                    claims.add(claim)
                }
                ac.evaluate { assert true }
            }
        }
        ac.await(10)
        pool.close()

        then:
        claims.size() == expected.size()
        claims.collect { requestKey(readBytes(it, payloadLength)) }.toSet() == expected

        when:
        claims.each { buffer.release(it) }

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should reuse blocks after producer cursor wraps around'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payloadLength = 20

        when:
        24.times { i ->
            def bytes = payload(10500 + i, payloadLength)
            assert buffer.write(sourceFromBytes(bytes, [5, 7, 8]))
            def claim = awaitClaim(buffer)
            assert readBytes(claim, payloadLength) == bytes
            buffer.release(claim)
        }

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    def 'should reuse blocks after consumer cursor wraps around'() {
        given:
        def buffer = TransportBuffer.create(64, 8)
        def payloadLength = 21
        def firstBatch = (0..<8).collect { payload(10600 + it, payloadLength) }
        firstBatch.each { assert buffer.write(sourceFromBytes(it, [9, 12])) }

        when:
        firstBatch.each {
            def claim = buffer.claim()
            assert claim != null
            assert readBytes(claim, payloadLength) == it
            buffer.release(claim)
        }
        def secondBatch = (0..<8).collect { payload(10700 + it, payloadLength) }
        secondBatch.each { assert buffer.write(sourceFromBytes(it, [7, 14])) }
        def secondClaims = drainClaims(buffer)

        then:
        secondClaims.size() == 8
        secondClaims.collect { requestKey(readBytes(it, payloadLength)) }.toSet() == secondBatch.collect { requestKey(it) }.toSet()
        buffer.freeBlockCount() == 0

        when:
        secondClaims.each { buffer.release(it) }

        then:
        buffer.freeBlockCount() == 8

        cleanup:
        buffer.close()
    }

    private static List<TransportBuffer.Block> drainClaims(TransportBuffer buffer) {
        def claims = [] as List<TransportBuffer.Block>
        while (true) {
            def claim = buffer.claim()
            if (claim == null) {
                break
            }
            claims.add(claim)
        }
        claims
    }

    private static TransportBuffer.Block awaitClaim(TransportBuffer buffer, int timeoutMillis = 5000) {
        long deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            def claim = buffer.claim()
            if (claim != null) {
                return claim
            }
            Thread.sleep(1)
        }
        assert false: 'Timed out waiting for claim'
        return null
    }

    private static void awaitUntil(AtomicBoolean flag, int timeoutMillis = 5000) {
        long deadline = System.currentTimeMillis() + timeoutMillis
        while (!flag.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(1)
        }
        assert flag.get()
    }

    private static List<TransportBuffer.Block> chain(TransportBuffer.Block head) {
        def blocks = [] as List<TransportBuffer.Block>
        def current = head
        while (current != null) {
            blocks.add(current)
            current = current.next()
        }
        blocks
    }

    private static String requestKey(byte[] payload) {
        Base64.encoder.encodeToString(payload)
    }

    private static byte[] payload(int seed, int length) {
        def data = new byte[length]
        for (int i = 0; i < length; i++) {
            data[i] = (byte) ((seed + i) % 127)
        }
        data
    }

    private static byte[] readBytes(TransportBuffer.Block head, int expectedLength) {
        def bytes = new byte[expectedLength]
        def offset = 0
        def current = head
        while (current != null && offset < expectedLength) {
            def remaining = expectedLength - offset
            def payload = current.payload()
            def readSize = Math.min((int) payload.byteSize(), remaining)
            def chunk = new byte[readSize]
            payload.asSlice(0, readSize).asByteBuffer().get(chunk)
            System.arraycopy(chunk, 0, bytes, offset, readSize)
            offset += readSize
            current = current.next()
        }
        assert offset == expectedLength
        bytes
    }

    private static TransportSource delayedSource(byte[] data, List<Integer> chunkSizes, int delayMillis) {
        new TransportSource() {
            private final MemorySegment source = MemorySegment.ofArray(data)
            private int offset = 0
            private int chunkIndex = 0

            @Override
            int read(MemorySegment destination) {
                if (offset >= data.length) {
                    return -1
                }
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis)
                }
                int maxWritable = (int) destination.byteSize()
                int requested = chunkSizes.isEmpty() ? Integer.MAX_VALUE : chunkSizes[Math.min(chunkIndex, chunkSizes.size() - 1)]
                chunkIndex++
                int available = data.length - offset
                int toWrite = Math.min(Math.min(requested, maxWritable), available)
                destination.asSlice(0, toWrite).copyFrom(source.asSlice(offset, toWrite))
                offset += toWrite
                return toWrite
            }
        }
    }

    private static TransportSource blockingEofSource(byte[] data, List<Integer> chunkSizes, AtomicBoolean eofReached, AtomicBoolean allowFinish) {
        new TransportSource() {
            private final MemorySegment source = MemorySegment.ofArray(data)
            private int offset = 0
            private int chunkIndex = 0

            @Override
            int read(MemorySegment destination) {
                if (offset >= data.length) {
                    eofReached.set(true)
                    while (!allowFinish.get()) {
                        Thread.sleep(1)
                    }
                    return -1
                }
                int maxWritable = (int) destination.byteSize()
                int requested = chunkSizes.isEmpty() ? Integer.MAX_VALUE : chunkSizes[Math.min(chunkIndex, chunkSizes.size() - 1)]
                chunkIndex++
                int available = data.length - offset
                int toWrite = Math.min(Math.min(requested, maxWritable), available)
                destination.asSlice(0, toWrite).copyFrom(source.asSlice(offset, toWrite))
                offset += toWrite
                return toWrite
            }
        }
    }

    private static TransportSource sourceFromBytes(byte[] data, List<Integer> chunkSizes = []) {
        new TransportSource() {
            private final MemorySegment source = MemorySegment.ofArray(data)
            private int offset = 0
            private int chunkIndex = 0

            @Override
            int read(MemorySegment destination) {
                if (offset >= data.length) {
                    return -1
                }
                int maxWritable = (int) destination.byteSize()
                int requested = chunkSizes.isEmpty() ? Integer.MAX_VALUE : chunkSizes[Math.min(chunkIndex, chunkSizes.size() - 1)]
                chunkIndex++
                int available = data.length - offset
                int toWrite = Math.min(Math.min(requested, maxWritable), available)
                destination.asSlice(0, toWrite).copyFrom(source.asSlice(offset, toWrite))
                offset += toWrite
                return toWrite
            }
        }
    }

    private static byte[] bytes(int length) {
        def data = new byte[length]
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i % 127)
        }
        data
    }
}
