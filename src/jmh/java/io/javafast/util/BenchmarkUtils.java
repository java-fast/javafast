package io.javafast.util;

import io.javafast.internal.ByteString;
import io.javafast.transport.TransportBuffer;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;

public class BenchmarkUtils {

    public static ByteString byteString(String value) {
        final TransportBuffer buffer = TransportBuffer.create(Math.max(value.length(), 64), 1);
        final TransportBuffer.Writer writer = buffer.writer();
        try {
            writer.read(new ReadableByteChannel() {
                @Override
                public int read(ByteBuffer dst) {
                    dst.put(value.getBytes(StandardCharsets.UTF_8));
                    return dst.position();
                }

                @Override
                public boolean isOpen() {
                    return false;
                }

                @Override
                public void close() {

                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        final ByteString bs = new ByteString();
        bs.reset(writer.reader(), 0, writer.totalWritten());
        return bs;
    }

}
