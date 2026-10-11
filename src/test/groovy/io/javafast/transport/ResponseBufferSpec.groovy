package io.javafast.transport

import spock.lang.Specification

class ResponseBufferSpec extends Specification {

    TransportBuffer.Reader reader() {
        TransportBuffer.create(64, 1).writer().reader()
    }

    def "should reject invalid capacities"() {
        when:
        ResponseBuffer.create(capacity)

        then:
        thrown(IllegalArgumentException)

        where:
        capacity << [0, -1, -2, 3, 5, 6, 7, 9, 15]
    }

    def "should accept power-of-two capacities"() {
        expect:
        ResponseBuffer.create(capacity) != null

        where:
        capacity << [1, 2, 4, 8, 16, 32]
    }

    def "should assign monotonically increasing sequences starting at zero"() {
        given:
        def buffer = ResponseBuffer.create(4)
        def readers = (0..<3).collect { reader() }

        expect:
        buffer.offer(readers[0]) == 0
        buffer.offer(readers[1]) == 1
        buffer.offer(readers[2]) == 2
        buffer.size() == 3
    }

    def "should reject offers when the buffer is full"() {
        given:
        def buffer = ResponseBuffer.create(2)

        when:
        def first = buffer.offer(reader())
        def second = buffer.offer(reader())
        def third = buffer.offer(reader())

        then:
        first == 0
        second == 1
        third == -1
        buffer.size() == 2
    }

    def "should not return a response before it is ready"() {
        given:
        def buffer = ResponseBuffer.create(2)
        def sequence = buffer.offer(reader())

        expect:
        sequence == 0
        buffer.take() == null
        buffer.size() == 1
    }

    def "should return only the next response in sequence order"() {
        given:
        def buffer = ResponseBuffer.create(4)
        def readers = (0..<3).collect { reader() }

        when: "later responses complete first"
        def s0 = buffer.offer(readers[0])
        def s1 = buffer.offer(readers[1])
        def s2 = buffer.offer(readers[2])

        buffer.update(s2, ResponseBuffer.SlotState.READY)
        buffer.update(s1, ResponseBuffer.SlotState.READY)

        then: "the head response is still not ready"
        buffer.take() == null

        when: "the head response completes"
        buffer.update(s0, ResponseBuffer.SlotState.READY)

        then: "responses become available in request order"
        def first = buffer.take()
        first.sequence() == s0
        first.reader().is(readers[0])
        first.state() == ResponseBuffer.SlotState.WRITING

        when:
        buffer.update(s0, ResponseBuffer.SlotState.FREE)

        then:
        def second = buffer.take()
        second.sequence() == s1
        second.reader().is(readers[1])

        when:
        buffer.update(s1, ResponseBuffer.SlotState.FREE)

        then:
        def third = buffer.take()
        third.sequence() == s2
        third.reader().is(readers[2])

        when:
        buffer.update(s2, ResponseBuffer.SlotState.FREE)

        then:
        buffer.size() == 0
        buffer.take() == null
    }

    def "should not allow a slot to be taken more than once while writing"() {
        given:
        def buffer = ResponseBuffer.create(2)
        def sequence = buffer.offer(reader())

        when:
        buffer.update(sequence, ResponseBuffer.SlotState.READY)

        then:
        def slot = buffer.take()
        slot.sequence() == sequence
        slot.state() == ResponseBuffer.SlotState.WRITING

        and:
        buffer.take() == null
    }

    def "should release a slot only after it has entered WRITING state"() {
        given:
        def buffer = ResponseBuffer.create(2)
        def sequence = buffer.offer(reader())

        when:
        buffer.update(sequence, ResponseBuffer.SlotState.FREE)

        then:
        thrown(IllegalStateException)

        when:
        buffer.update(sequence, ResponseBuffer.SlotState.READY)
        buffer.update(sequence, ResponseBuffer.SlotState.FREE)

        then:
        thrown(IllegalStateException)
    }

    def "should only release the head slot"() {
        given:
        def buffer = ResponseBuffer.create(4)
        def first = buffer.offer(reader())
        def second = buffer.offer(reader())

        buffer.update(first, ResponseBuffer.SlotState.READY)
        buffer.update(second, ResponseBuffer.SlotState.READY)
        buffer.take()

        when:
        buffer.update(second, ResponseBuffer.SlotState.FREE)

        then:
        thrown(IllegalStateException)
    }

    def "should reject invalid READY transitions"() {
        given:
        def buffer = ResponseBuffer.create(2)
        def sequence = buffer.offer(reader())

        when:
        buffer.update(sequence, ResponseBuffer.SlotState.READY)
        buffer.update(sequence, ResponseBuffer.SlotState.READY)

        then:
        thrown(IllegalStateException)
    }

    def "should reject unknown and expired sequences"() {
        given:
        def buffer = ResponseBuffer.create(2)
        def sequence = buffer.offer(reader())

        when: "the sequence has never been offered"
        buffer.update(10, ResponseBuffer.SlotState.READY)

        then:
        thrown(IllegalArgumentException)

        when: "the response is completed and released"
        buffer.update(sequence, ResponseBuffer.SlotState.READY)
        buffer.take()
        buffer.update(sequence, ResponseBuffer.SlotState.FREE)
        buffer.update(sequence, ResponseBuffer.SlotState.READY)

        then:
        thrown(IllegalArgumentException)
    }

    def "should clear the reader when a response is released"() {
        given:
        def buffer = ResponseBuffer.create(2)
        def originalReader = reader()
        def sequence = buffer.offer(originalReader)

        when:
        buffer.update(sequence, ResponseBuffer.SlotState.READY)
        def slot = buffer.take()

        then:
        slot.reader().is(originalReader)

        when:
        buffer.update(sequence, ResponseBuffer.SlotState.FREE)

        then:
        slot.reader() == null
        slot.state() == ResponseBuffer.SlotState.FREE
        buffer.size() == 0
    }

    def "should reuse slots after the ring wraps around"() {
        given:
        def buffer = ResponseBuffer.create(2)
        def firstReader = reader()
        def secondReader = reader()
        def thirdReader = reader()

        when:
        def firstSequence = buffer.offer(firstReader)
        buffer.update(firstSequence, ResponseBuffer.SlotState.READY)
        def firstSlot = buffer.take()

        then:
        firstSequence == 0
        firstSlot.sequence() == firstSequence

        when:
        buffer.update(firstSequence, ResponseBuffer.SlotState.FREE)

        then:
        buffer.offer(secondReader) == 1
        buffer.size() == 1

        and:
        buffer.offer(thirdReader) == 2
        buffer.size() == 2
        buffer.slots[0].sequence() == 2

    }

    def "should preserve FIFO order across multiple ring wrap-arounds"() {
        given:
        def buffer = ResponseBuffer.create(2)

        expect:
        (0..<10).every { expected ->
            def sequence = buffer.offer(reader())
            if (sequence != expected) {
                return false
            }

            buffer.update(sequence, ResponseBuffer.SlotState.READY)
            def slot = buffer.take()

            if (slot == null || slot.sequence() != expected) {
                return false
            }

            buffer.update(sequence, ResponseBuffer.SlotState.FREE)
            buffer.size() == 0
        }
    }
}
