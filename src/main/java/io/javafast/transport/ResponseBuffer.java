package io.javafast.transport;

import io.javafast.util.Utils;

final class ResponseBuffer {

    static ResponseBuffer create(int capacity) {
        return new ResponseBuffer(capacity);
    }

    enum SlotState {
        FREE,
        PROCESSING,
        READY,
        WRITING
    }

    static final class ResponseSlot {
        private volatile long sequence = -1;
        private volatile SlotState state = SlotState.FREE;
        private TransportBuffer.Writer writer;

        long sequence() {
            return this.sequence;
        }

        TransportBuffer.Writer writer() {
            return this.writer;
        }

        SlotState state() {
            return this.state;
        }
    }

    private final ResponseSlot[] slots;
    private final int mask;

    // selector thread owns offer sequence, volatile reads by VTs
    private volatile long nextOfferSequence;

    // selector thread owns write sequence, volatile reads by VTs
    private volatile long nextWriteSequence;

    private ResponseBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (Utils.isNotPowerOfTwo(capacity)) {
            throw new IllegalArgumentException("capacity must be power of two");
        }
        this.slots = new ResponseSlot[capacity];
        this.mask = capacity - 1;
        for (int i = 0; i < capacity; i++) {
            this.slots[i] = new ResponseSlot();
        }
    }

    /**
     * Only called by selector thread
     */
    long offer(TransportBuffer.Writer writer) {
        final long sequence = this.nextOfferSequence;
        if (sequence - this.nextWriteSequence >= this.slots.length) {
            return -1; // buffer is full
        }
        final ResponseSlot slot = this.slots[index(sequence)];
        slot.sequence = sequence;
        slot.writer = writer;
        slot.state = SlotState.PROCESSING;
        this.nextOfferSequence = sequence + 1; // publish only after complete initialization
        return sequence;
    }

    /**
     * Called by VT processors and selector thread
     */
    boolean update(long sequence, SlotState state) {
        final ResponseSlot slot = find(sequence);
        switch (state) {
            case READY -> { // only called by VTs
                if (slot.state != SlotState.PROCESSING) {
                    throw new IllegalStateException("Cannot mark " + slot.state + " slot as READY");
                }
                slot.state = SlotState.READY;
                return sequence == this.nextWriteSequence; // now selector can write to the socket
            }
            case FREE -> { // only called by selector thread
                final long next = this.nextWriteSequence;
                if (sequence != next) {
                    throw new IllegalStateException("Only the head slot can be released");
                }
                if (slot.state != SlotState.WRITING) {
                    throw new IllegalStateException("Cannot release a slot that is not WRITING");
                }
                slot.writer = null;
                slot.state = SlotState.FREE;
                this.nextWriteSequence = next + 1; // publish only after complete initialization
                return false;
            }
            default -> throw new IllegalArgumentException("State transition must be to READY or FREE");
        }
    }

    /**
     * Only called by selector thread
     */
    ResponseSlot take() {
        if (this.nextWriteSequence == this.nextOfferSequence) {
            return null;
        }
        final ResponseSlot slot = this.slots[index(this.nextWriteSequence)];
        if (slot.sequence != this.nextWriteSequence || slot.state != SlotState.READY) {
            return null; // Slot is not ready for writing
        }
        slot.state = SlotState.WRITING;
        return slot;
    }

    private ResponseSlot find(long sequence) {
        if (sequence < this.nextWriteSequence || sequence >= this.nextOfferSequence) {
            throw new IllegalArgumentException("Unknown or expired sequence: " + sequence);
        }
        final ResponseSlot slot = this.slots[index(sequence)];
        if (slot.sequence != sequence || slot.state == SlotState.FREE) {
            throw new IllegalArgumentException("Slot is no longer valid for sequence: " + sequence);
        }
        return slot;
    }

    private int index(long sequence) {
        return (int) (sequence & this.mask);
    }

    int size() {
        return (int) (this.nextOfferSequence - this.nextWriteSequence);
    }

}
