package io.javafast.transport;

import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark                                                Mode  Cnt   Score    Error   Units
 * IndexOfBenchmark.indexOf                                 avgt    5   4.576 ±  0.021   ns/op
 * IndexOfBenchmark.indexOf:·gc.alloc.rate                  avgt    5  ≈ 10⁻⁴           MB/sec
 * IndexOfBenchmark.indexOf:·gc.alloc.rate.norm             avgt    5  ≈ 10⁻⁶             B/op
 * IndexOfBenchmark.indexOf:·gc.count                       avgt    5     ≈ 0           counts
 * IndexOfBenchmark.indexOf2                                avgt    5  15.007 ±  0.019   ns/op
 * IndexOfBenchmark.indexOf2:·gc.alloc.rate                 avgt    5  ≈ 10⁻⁴           MB/sec
 * IndexOfBenchmark.indexOf2:·gc.alloc.rate.norm            avgt    5  ≈ 10⁻⁶             B/op
 * IndexOfBenchmark.indexOf2:·gc.count                      avgt    5     ≈ 0           counts
 * IndexOfBenchmark.indexOf3                                avgt    5  19.554 ±  0.049   ns/op
 * IndexOfBenchmark.indexOf3:·gc.alloc.rate                 avgt    5  ≈ 10⁻⁴           MB/sec
 * IndexOfBenchmark.indexOf3:·gc.alloc.rate.norm            avgt    5  ≈ 10⁻⁶             B/op
 * IndexOfBenchmark.indexOf3:·gc.count                      avgt    5     ≈ 0           counts
 * IndexOfBenchmark.indexOfVectorized                       avgt    5   1.819 ±  0.008   ns/op
 * IndexOfBenchmark.indexOfVectorized:·gc.alloc.rate        avgt    5  ≈ 10⁻⁴           MB/sec
 * IndexOfBenchmark.indexOfVectorized:·gc.alloc.rate.norm   avgt    5  ≈ 10⁻⁷             B/op
 * IndexOfBenchmark.indexOfVectorized:·gc.count             avgt    5     ≈ 0           counts
 * IndexOfBenchmark.indexOfVectorized2                      avgt    5   7.377 ±  0.013   ns/op
 * IndexOfBenchmark.indexOfVectorized2:·gc.alloc.rate       avgt    5  ≈ 10⁻⁴           MB/sec
 * IndexOfBenchmark.indexOfVectorized2:·gc.alloc.rate.norm  avgt    5  ≈ 10⁻⁶             B/op
 * IndexOfBenchmark.indexOfVectorized2:·gc.count            avgt    5     ≈ 0           counts
 * IndexOfBenchmark.indexOfVectorized3                      avgt    5  13.788 ±  0.065   ns/op
 * IndexOfBenchmark.indexOfVectorized3:·gc.alloc.rate       avgt    5  ≈ 10⁻⁴           MB/sec
 * IndexOfBenchmark.indexOfVectorized3:·gc.alloc.rate.norm  avgt    5  ≈ 10⁻⁶             B/op
 * IndexOfBenchmark.indexOfVectorized3:·gc.count            avgt    5     ≈ 0           counts
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class IndexOfBenchmark {

    private static final byte COLON = ':';
    private static final byte CR = '\r';
    private static final byte LF = '\n';
    private static final byte SPACE = ' ';
    private static final byte HTAB = '\t';

    private TransportBuffer buffer;
    private TransportBuffer.Reader reader;
    private TransportBuffer bufferUseVectors;
    private TransportBuffer.Reader readerUseVectors;

    @Setup(Level.Trial)
    public void setup() {
        this.buffer = TransportBuffer.create(128, 1);
        this.bufferUseVectors = TransportBuffer.create(128, 1, true);
        final TransportBuffer.Writer writer1 = this.buffer.writer();
        final TransportBuffer.Writer writer2 = this.bufferUseVectors.writer();
        final String text120Bytes = "content-type: text/plain; charset=utf-8   \r\n";
        try {
            writer1.read(text120Bytes);
            writer2.read(text120Bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.reader = writer1.reader();
        this.readerUseVectors = writer2.reader();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        this.buffer.close();
        this.bufferUseVectors.close();
    }

    @Benchmark
    public int indexOf() {
        final int pos;
        int sum = 0;
        if ((pos = this.reader.indexOf(COLON)) > -1) {
            sum += pos;
        }
        this.reader.reset();
        return sum;
    }

    @Benchmark
    public int indexOf2() {
        final int pos;
        int sum = 0;
        if ((pos = this.reader.indexOf(CR, LF)) > -1) {
            sum += pos;
        }
        this.reader.reset();
        return sum;
    }

    @Benchmark
    public int indexOf3() {
        int pos;
        int sum = 0;
        if ((pos = this.reader.indexOf(CR, LF)) > -1) {
            this.reader.skip(pos);
            while (this.reader.unconsume(SPACE) || this.reader.unconsume(HTAB)) {
                pos--; // trim trailing OWS
            }
            sum += pos;
        }
        this.reader.reset();
        return sum;
    }


    @Benchmark
    public int indexOfVectorized() {
        final int pos;
        int sum = 0;
        if ((pos = this.readerUseVectors.indexOf(COLON)) > -1) {
            sum += pos;
        }
        this.readerUseVectors.reset();
        return sum;
    }

    @Benchmark
    public int indexOfVectorized2() {
        final int pos;
        int sum = 0;
        if ((pos = this.readerUseVectors.indexOf(CR, LF)) > -1) {
            sum += pos;
        }
        this.readerUseVectors.reset();
        return sum;
    }

    @Benchmark
    public int indexOfVectorized3() {
        int pos;
        int sum = 0;
        if ((pos = this.readerUseVectors.indexOf(CR, LF)) > -1) {
            this.readerUseVectors.skip(pos);
            while (this.readerUseVectors.unconsume(SPACE) || this.readerUseVectors.unconsume(HTAB)) {
                pos--; // trim trailing OWS
            }
            sum += pos;
        }
        this.readerUseVectors.reset();
        return sum;
    }

}
