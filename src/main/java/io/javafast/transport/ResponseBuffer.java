package io.javafast.transport;

import io.javafast.util.Utils;

final class ResponseBuffer {

    static ResponseBuffer create(int capacity) {
        return new ResponseBuffer(capacity);
    }

    enum SlotState {
        FREE,
        PROCESSING,
        ABORTED,
        READY,
        WRITING
    }

    static final class ResponseSlot {
        private volatile long sequence = -1;
        private volatile SlotState state = SlotState.FREE;
        private TransportBuffer.Writer writer;
        private boolean connectionClose;

        long sequence() {
            return this.sequence;
        }

        TransportBuffer.Writer writer() {
            return this.writer;
        }

        SlotState state() {
            return this.state;
        }

        boolean connectionClose() {
            return this.connectionClose;
        }
    }

    private final ResponseSlot[] slots;
    private final int mask;

    // selector thread owns offer sequence, volatile reads by VTs
    private volatile long nextOfferSequence;

    // selector thread owns write sequence, volatile reads by VTs
    private volatile long nextWriteSequence;

    // selector thread owns closed, volatile reads by VTs
    private volatile boolean closed;

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
     * Only called by selector thread.
     * Removes aborted head slots and releases their writers.
     */
    private void discardAbortedHead() {
        while (this.nextWriteSequence != this.nextOfferSequence) {
            final ResponseSlot slot = this.slots[index(this.nextWriteSequence)];
            if (slot.sequence != this.nextWriteSequence || slot.state != SlotState.ABORTED) {
                return;
            }
            final long sequence = this.nextWriteSequence;
            final TransportBuffer.Writer writer = slot.writer;
            if (writer != null) {
                writer.release();
            }
            slot.writer = null;
            slot.state = SlotState.FREE;
            this.nextWriteSequence = sequence + 1;
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
    boolean update(long sequence, SlotState state, boolean connectionClose) {
        final ResponseSlot slot = find(sequence);
        switch (state) {
            case READY -> { // only called by VTs
                if (slot.state != SlotState.PROCESSING) {
                    throw new IllegalStateException("Cannot mark " + slot.state + " slot as READY");
                }
                slot.connectionClose = connectionClose || this.closed;
                slot.state = SlotState.READY; // connectionClose is visible due to happens-before
                return sequence == this.nextWriteSequence; // now selector can write to the socket
            }
            case FREE -> { // only called by selector thread
                if (this.closed) {
                    releaseSlotWriter(slot);
                    return false;
                }
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
        discardAbortedHead();
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

    /**
     * Called by VT processors.
     * The selector still owns sequence advancement and writer release.
     */
    void abort(long sequence) {
        final ResponseSlot slot = find(sequence);
        if (slot.state != SlotState.PROCESSING) {
            throw new IllegalStateException("Cannot abort " + slot.state + " slot");
        }
        // Volatile write publishes the aborted state to the selector
        slot.state = SlotState.ABORTED;
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

    private void releaseSlotWriter(ResponseSlot slot) {
        final TransportBuffer.Writer writer = slot.writer;
        if (writer != null) {
            writer.release();
        }
        slot.writer = null;
        slot.state = SlotState.FREE;
    }

    void close() {
        this.closed = true;
        for (ResponseSlot slot : this.slots) {
            final SlotState state = slot.state;
            if (state == SlotState.READY || state == SlotState.ABORTED) {
                releaseSlotWriter(slot);
            }
            // NOTE: DO NOT release PROCESSING here!
            // Some workers may still be using the writer
            // Workers will signal the selector when they are done
        }
    }

}
