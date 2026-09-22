package io.javafast.transport

import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

import java.lang.foreign.MemorySegment
import java.util.Arrays
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class TransferBufferStressTestSpec extends Specification {

    def 'should preserve all requests under concurrent producer and consumer load'() {
        given:
        def blockCount = 64
        def buffer = TransportBuffer.create(64, blockCount)
        def totalRequests = 300
        def payloadLength = 180
        def producerThreads = 6
        def consumerThreads = 6
        def ac = new AsyncConditions(producerThreads + consumerThreads)
        def pool = Executors.newFixedThreadPool(producerThreads + consumerThreads)
        def produced = new AtomicInteger(0)
        def consumed = new ConcurrentLinkedQueue<String>()
        Set<String> expected = (0..<totalRequests)
            .collect { requestKey(payload(11000 + it, payloadLength)) }
            .toSet()

        when:
        producerThreads.times {
            pool.submit {
                while (true) {
                    def id = produced.getAndIncrement()
                    if (id >= totalRequests) {
                        break
                    }
                    writeEventually(buffer, payload(11000 + id, payloadLength), [11, 13, 17, 19, 23])
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
        ac.await(45)
        pool.close()

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
        def ac = new AsyncConditions(producerThreads + consumerThreads)
        def pool = Executors.newFixedThreadPool(producerThreads + consumerThreads)
        def produced = new AtomicInteger(0)
        def consumed = new ConcurrentLinkedQueue<String>()
        def mismatches = new AtomicInteger(0)
        Map<String, byte[]> expectedPayloads = (0..<totalRequests).collectEntries {
            def bytes = payload(12000 + it, payloadLength)
            [requestKey(bytes), bytes]
        }

        when:
        producerThreads.times {
            pool.submit {
                while (true) {
                    def id = produced.getAndIncrement()
                    if (id >= totalRequests) {
                        break
                    }
                    writeEventually(buffer, payload(12000 + id, payloadLength), [7, 9, 13, 17, 21, 25])
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
                    def actual = readBytes(claim, payloadLength)
                    def key = requestKey(actual)
                    def expected = expectedPayloads[key]
                    if (expected == null || !Arrays.equals(expected, actual)) {
                        mismatches.incrementAndGet()
                    }
                    consumed.add(key)
                    buffer.release(claim)
                }
                ac.evaluate { assert true }
            }
        }
        ac.await(45)
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
        def ac = new AsyncConditions(producerThreads + consumerThreads)
        def pool = Executors.newFixedThreadPool(producerThreads + consumerThreads)
        def produced = new AtomicInteger(0)
        def consumed = new AtomicInteger(0)

        when:
        producerThreads.times {
            pool.submit {
                while (true) {
                    def id = produced.getAndIncrement()
                    if (id >= totalRequests) {
                        break
                    }
                    writeEventually(buffer, payload(13000 + id, payloadLength), [9, 14, 18, 22, 27])
                }
                ac.evaluate { assert true }
            }
        }
        consumerThreads.times {
            pool.submit {
                while (consumed.get() < totalRequests) {
                    def claim = buffer.claim()
                    if (claim == null) {
                        Thread.sleep(1)
                        continue
                    }
                    readBytes(claim, payloadLength)
                    buffer.release(claim)
                    consumed.incrementAndGet()
                }
                ac.evaluate { assert true }
            }
        }
        ac.await(45)
        pool.close()

        then:
        consumed.get() == totalRequests
        awaitAtMost(5000) { buffer.freeBlockCount() == blockCount }
        buffer.claim() == null

        cleanup:
        buffer.close()
    }

    private static void writeEventually(TransportBuffer buffer, byte[] data, List<Integer> chunkSizes) {
        while (!buffer.write(sourceFromBytes(data, chunkSizes))) {
            Thread.sleep(1)
        }
    }

    private static boolean awaitAtMost(int timeoutMillis, Closure<Boolean> condition) {
        long deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition.call()) {
                return true
            }
            Thread.sleep(1)
        }
        condition.call()
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
                toWrite
            }
        }
    }

}
