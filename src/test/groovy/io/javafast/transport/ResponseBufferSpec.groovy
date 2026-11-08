package io.javafast.transport

import spock.lang.Specification

class ResponseBufferSpec extends Specification {

    TransportBuffer.Writer writer() {
        TransportBuffer.create(64, 1).writer()
    }

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
        def writers = (0..<3).collect { writer() }

        expect:
        buffer.offer(writers[0]) == 0
        buffer.offer(writers[1]) == 1
        buffer.offer(writers[2]) == 2
        buffer.size() == 3
    }

    def "should reject offers when the buffer is full"() {
        given:
        def buffer = ResponseBuffer.create(2)

        when:
        def first = buffer.offer(writer())
        def second = buffer.offer(writer())
        def third = buffer.offer(writer())

        then:
        first == 0
        second == 1
        third == -1
        buffer.size() == 2
    }

    def "should not return a response before it is ready"() {
        given:
        def buffer = ResponseBuffer.create(2)
        def sequence = buffer.offer(writer())

        expect:
        sequence == 0
        buffer.take() == null
        buffer.size() == 1
    }

    def "should return only the next response in sequence order"() {
        given:
        def buffer = ResponseBuffer.create(4)
        def writers = (0..<3).collect { writer() }

        when: "later responses complete first"
        def s0 = buffer.offer(writers[0])
        def s1 = buffer.offer(writers[1])
        def s2 = buffer.offer(writers[2])

        buffer.update(s2, ResponseBuffer.SlotState.READY)
        buffer.update(s1, ResponseBuffer.SlotState.READY)

        then: "the head response is still not ready"
        buffer.take() == null

        when: "the head response completes"
        buffer.update(s0, ResponseBuffer.SlotState.READY)

        then: "responses become available in request order"
        def first = buffer.take()
        first.sequence() == s0
        first.writer().is(writers[0])
        first.state() == ResponseBuffer.SlotState.WRITING

        when:
        buffer.update(s0, ResponseBuffer.SlotState.FREE)

        then:
        def second = buffer.take()
        second.sequence() == s1
        second.writer().is(writers[1])

        when:
        buffer.update(s1, ResponseBuffer.SlotState.FREE)

        then:
        def third = buffer.take()
        third.sequence() == s2
        third.writer().is(writers[2])

        when:
        buffer.update(s2, ResponseBuffer.SlotState.FREE)

        then:
        buffer.size() == 0
        buffer.take() == null
    }

    def "should not allow a slot to be taken more than once while writing"() {
        given:
        def buffer = ResponseBuffer.create(2)
        def sequence = buffer.offer(writer())

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
        def sequence = buffer.offer(writer())

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
        def first = buffer.offer(writer())
        def second = buffer.offer(writer())

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
        def sequence = buffer.offer(writer())

        when:
        buffer.update(sequence, ResponseBuffer.SlotState.READY)
        buffer.update(sequence, ResponseBuffer.SlotState.READY)

        then:
        thrown(IllegalStateException)
    }

    def "should reject unknown and expired sequences"() {
        given:
        def buffer = ResponseBuffer.create(2)
        def sequence = buffer.offer(writer())

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
        def originalWriter = writer()
        def sequence = buffer.offer(originalWriter)

        when:
        buffer.update(sequence, ResponseBuffer.SlotState.READY)
        def slot = buffer.take()

        then:
        slot.writer().is(originalWriter)

        when:
        buffer.update(sequence, ResponseBuffer.SlotState.FREE)

        then:
        slot.writer() == null
        slot.state() == ResponseBuffer.SlotState.FREE
        buffer.size() == 0
    }

    def "should reuse slots after the ring wraps around"() {
        given:
        def buffer = ResponseBuffer.create(2)
        def firstWriter = writer()
        def secondWriter = writer()
        def thirdWriter = writer()

        when:
        def firstSequence = buffer.offer(firstWriter)
        buffer.update(firstSequence, ResponseBuffer.SlotState.READY)
        def firstSlot = buffer.take()

        then:
        firstSequence == 0
        firstSlot.sequence() == firstSequence

        when:
        buffer.update(firstSequence, ResponseBuffer.SlotState.FREE)

        then:
        buffer.offer(secondWriter) == 1
        buffer.size() == 1

        and:
        buffer.offer(thirdWriter) == 2
        buffer.size() == 2
        buffer.slots[0].sequence() == 2

    }

    def "should preserve FIFO order across multiple ring wrap-arounds"() {
        given:
        def buffer = ResponseBuffer.create(2)

        expect:
        (0..<10).every { expected ->
            def sequence = buffer.offer(writer())
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
