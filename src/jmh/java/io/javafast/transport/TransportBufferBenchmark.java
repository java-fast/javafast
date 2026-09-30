package io.javafast.transport;

import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class TransportBufferBenchmark {

    private static final int BLOCK_COUNT = 1024;

    private TransportBuffer buffer;
    private FixedSource source;

    @Param({
//        "256",
//        "512",
        "1024",
//        "2048",
//        "4096",
//        "8192",
//        "16384"
    })
    int blockSize;

    @Param({
//        "256",
//        "1024",
        "4096",
//        "16384",
//        "65536"
    })
    int requestSize;

    @Setup(Level.Trial)
    public void setup() {
        this.buffer = TransportBuffer.create(this.blockSize, BLOCK_COUNT);
        this.source = new FixedSource(this.requestSize);
    }

    @Setup(Level.Invocation)
    public void prepareSource() {
        this.source.reset();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        this.buffer.close();
    }

    @Benchmark
    public int bufferWrite() throws IOException {
        final TransportBuffer.Writer writer = this.buffer.writer();
        if (writer == null) {
            throw new IllegalStateException("acquire returned null");
        }
        while (true) {
            if (writer.read(this.source) < 0) break;
        }
        writer.release();
        return writer.totalWritten();
    }
}
