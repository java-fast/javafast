package io.javafast.transport;

final class ResponseBuffer {

    static ResponseBuffer create(int capacity) {
        return new ResponseBuffer(capacity);
    }

    private static boolean isNotPowerOfTwo(int n) {
        return n == 0 || (n & n - 1) != 0;
    }

    enum SlotState {
        FREE,
        PROCESSING,
        READY,
        WRITING
    }

    final class ResponseSlot {
        private volatile long sequence = -1;
        private volatile SlotState state = SlotState.FREE;
        private TransportBuffer.Reader reader;

        long sequence() {
            return this.sequence;
        }

        TransportBuffer.Reader reader() {
            return this.reader;
        }

        SlotState state() {
            return this.state;
        }
    }

    private final ResponseSlot[] slots;
    private final int mask;

    private long nextOfferSequence;
    private volatile long nextWriteSequence;

    private ResponseBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (ResponseBuffer.isNotPowerOfTwo(capacity)) {
            throw new IllegalArgumentException("capacity must be power of two");
        }
        this.slots = new ResponseSlot[capacity];
        this.mask = capacity - 1;
        for (int i = 0; i < capacity; i++) {
            this.slots[i] = new ResponseSlot();
        }
    }

    /**
     * Only called by Selector thread
     */
    long offer(TransportBuffer.Reader reader) {
        if (this.nextOfferSequence - this.nextWriteSequence >= this.slots.length) {
            return -1; // buffer is full
        }
        final long sequence = this.nextOfferSequence++;
        final ResponseSlot slot = this.slots[index(sequence)];
        slot.sequence = sequence;
        slot.reader = reader;
        slot.state = SlotState.PROCESSING;
        return sequence;
    }

    boolean update(long sequence, SlotState state) {
        final ResponseSlot slot = find(sequence);
        switch (state) {
            case READY -> {
                // only called by VTs
                if (slot.state != SlotState.PROCESSING) {
                    throw new IllegalStateException("Cannot mark " + slot.state + " slot as READY");
                }
                slot.state = SlotState.READY;
                return sequence == this.nextWriteSequence; // volatile read by VT Threads
            }
            case FREE -> {
                // only called by Selector thread
                if (sequence != this.nextWriteSequence) {
                    throw new IllegalStateException("Only the head slot can be released");
                }
                if (slot.state != SlotState.WRITING) {
                    throw new IllegalStateException("Cannot release a slot that is not WRITING");
                }
                slot.reader = null;
                slot.state = SlotState.FREE;
                this.nextWriteSequence++;
                return false;
            }
            default -> throw new IllegalArgumentException("State transition must be to READY or FREE");
        }
    }

    /**
     * Only called by Selector thread
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

    /**
     * Only called by Selector thread
     */
    int size() {
        return (int) (this.nextOfferSequence - this.nextWriteSequence);
    }

}
