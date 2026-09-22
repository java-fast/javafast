package io.javafast.transport;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.TimeUnit;

/**
 * Benchmark                                                      Mode  Cnt    Score    Error   Units
 * TransportBufferBenchmark.requestLifecycle                      avgt    5  658.299 ±  1.236   ns/op
 * TransportBufferBenchmark.requestLifecycle:·gc.alloc.rate       avgt    5   ≈ 10⁻⁴           MB/sec
 * TransportBufferBenchmark.requestLifecycle:·gc.alloc.rate.norm  avgt    5   ≈ 10⁻⁴             B/op
 * TransportBufferBenchmark.requestLifecycle:·gc.count            avgt    5      ≈ 0           counts
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class TransportBufferBenchmark {

    private static final int BLOCK_SIZE = 256;
    private static final int BLOCK_COUNT = 1024;
    private static final int REQUEST_SIZE = 4096;

    private TransportBuffer buffer;
    private FixedSource source;

    @Setup(Level.Trial)
    public void setup() {
        this.buffer = TransportBuffer.create(BLOCK_SIZE, BLOCK_COUNT);
        this.source = new FixedSource(REQUEST_SIZE);
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
    public int requestLifecycle() {
        if (!this.buffer.write(this.source)) {
            throw new IllegalStateException("write failed");
        }

        final TransportBuffer.Block claimed = this.buffer.claim();
        if (claimed == null) {
            throw new IllegalStateException("claim returned null");
        }

        int blocks = 0;
        TransportBuffer.Block current = claimed;
        while (current != null) {
            blocks++;
            current = current.next();
        }
        this.buffer.release(claimed);
        return blocks;
    }
}
