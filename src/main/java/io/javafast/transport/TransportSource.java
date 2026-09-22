package io.javafast.transport;

import java.lang.foreign.MemorySegment;

public interface TransportSource {

    /**
     * Fills destination
     * @return POSITIVE for number of bytes written, -1 for source finished
     */
    int read(MemorySegment destination);
}
