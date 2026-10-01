package io.javafast.transport

import io.javafast.test.TransportUtils
import spock.lang.RepeatUntilFailure
import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

import java.nio.BufferOverflowException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class TransportBufferStressTestSpec extends Specification implements TransportUtils {

    def 'should preserve all requests under concurrent producer and consumer load'() {
        given:
        def blockCount = 64
        def buffer = TransportBuffer.create(64, blockCount)
        def totalRequests = 300
        def payloadLength = 180
        def producerThreads = 6
        def consumerThreads = 6
        def ac = new AsyncConditions(totalRequests)
        def producerPool = Executors.newFixedThreadPool(producerThreads)
        def consumerPool = Executors.newFixedThreadPool(consumerThreads)
        def produced = new AtomicInteger(0)
        def consumed = new ConcurrentLinkedQueue<String>()
        Set<String> expected = (0..<totalRequests)
            .collect { requestKey(bytes(11000 + it, payloadLength)) }
            .toSet()

        when:
        producerThreads.times {
            producerPool.submit {
                try {
                    while (true) {
                        def id = produced.getAndIncrement()
                        if (id >= totalRequests) {
                            break
                        }
                        def claim = writeEventually(buffer, bytes(11000 + id, payloadLength), [11, 13, 17, 19, 23])
                        final localClaim = claim
                        consumerPool.submit { // publish to consumer threads
                            try {
                                consumed.add(requestKey(readBytes(buffer, localClaim, payloadLength)))
                            } catch (Throwable e) {
                                e.printStackTrace()
                            } finally {
                                localClaim.release()
                            }
                            ac.evaluate { assert true }
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace()
                }
            }
        }
        ac.await(5)
        producerPool.close()
        consumerPool.close()

        then:
        consumed.size() == totalRequests
        consumed.toSet() == expected
        buffer.freeBlockCount() == blockCount

        cleanup:
        buffer.close()
    }

    def 'should not corrupt the buffer under concurrent producer and consumer load'() {
        given:
        def blockCount = 64
        def buffer = TransportBuffer.create(64, blockCount)
        def totalRequests = 320
        def payloadLength = 210
        def producerThreads = 8
        def consumerThreads = 8
        def ac = new AsyncConditions(totalRequests)
        def pool = Executors.newFixedThreadPool(producerThreads + consumerThreads)
        def produced = new AtomicInteger(0)
        def consumed = new ConcurrentLinkedQueue<String>()
        def mismatches = new AtomicInteger(0)
        Map<String, byte[]> expectedPayloads = (0..<totalRequests).collectEntries {
            def bytes = bytes(12000 + it, payloadLength)
            [requestKey(bytes), bytes]
        }

        when:
        producerThreads.times {
            pool.submit {
                try {
                    while (true) {
                        def id = produced.getAndIncrement()
                        if (id >= totalRequests) {
                            break
                        }
                        def claim = writeEventually(buffer, bytes(12000 + id, payloadLength), [7, 9, 13, 17, 21, 25])
                        final localClaim = claim
                        pool.submit {
                            try {
                                def actual = readBytes(buffer, localClaim, payloadLength)
                                def key = requestKey(actual)
                                def expected = expectedPayloads[key]
                                if (expected == null || !Arrays.equals(expected, actual)) {
                                    mismatches.incrementAndGet()
                                }
                                consumed.add(key)
                            } catch (Throwable e) {
                                e.printStackTrace()
                            } finally {
                                localClaim.release()
                            }
                            ac.evaluate { assert true }
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace()
                }
            }
        }
        ac.await(5)
        pool.close()

        then:
        consumed.size() == totalRequests
        consumed.toSet() == expectedPayloads.keySet()
        mismatches.get() == 0
        buffer.freeBlockCount() == blockCount

        cleanup:
        buffer.close()
    }

    def 'should eventually release all blocks under concurrent load'() {
        given:
        def blockCount = 64
        def buffer = TransportBuffer.create(64, blockCount)
        def totalRequests = 360
        def payloadLength = 230
        def producerThreads = 8
        def consumerThreads = 8
        def ac = new AsyncConditions(totalRequests)
        def pool = Executors.newFixedThreadPool(producerThreads + consumerThreads)
        def produced = new AtomicInteger(0)
        def consumed = new AtomicInteger(0)

        when:
        producerThreads.times {
            pool.submit {
                try {
                    while (true) {
                        def id = produced.getAndIncrement()
                        if (id >= totalRequests) {
                            break
                        }
                        def claim = writeEventually(buffer, bytes(13000 + id, payloadLength), [9, 14, 18, 22, 27])
                        final localClaim = claim
                        pool.submit {
                            try {
                                readBytes(buffer, localClaim, payloadLength)
                                consumed.incrementAndGet()
                            } catch (Throwable e) {
                                e.printStackTrace()
                            } finally {
                                localClaim.release()
                            }
                            ac.evaluate { assert true }
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace()
                }
            }
        }
        ac.await(5)
        pool.close()

        then:
        consumed.get() == totalRequests
        buffer.freeBlockCount() == blockCount

        cleanup:
        buffer.close()
    }

    private TransportBuffer.Block writeEventually(TransportBuffer buffer, byte[] data, List<Integer> chunkSizes) {
        while (true) {
            def writer = buffer.writer()
            if (writer == null) {
                Thread.sleep(1)
                continue
            }
            def source = transportSource(data, chunkSizes)
            final long time = System.currentTimeMillis()
            while (System.currentTimeMillis() - time < 100) { // read timeout 100 milliseconds
                if (writer.read(source) < 0) break
            }
            return writer.head()
        }
    }

}
