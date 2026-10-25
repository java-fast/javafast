package io.javafast.transport;

import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class IndexOfBenchmark {

    public static final byte COLON = (byte) ':';

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
        final String text120Bytes = "qwejkhasldknlasdqweqlweljlxasnalskdxnaldxasdxnasldxnasdlasndxalsdnxasdxqwejkhasldknlasdqweqlweljlxasnalskdxnaldxasd:nasi";
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
        int pos;
        int sum = 0;
        while ((pos = this.reader.indexOf(COLON)) > -1) {
            this.reader.skip(pos + 1);
            sum += pos;
        }
        this.reader.reset();
        return sum;
    }

    @Benchmark
    public int indexOfVectorized() {
        int pos;
        int sum = 0;
        while ((pos = this.readerUseVectors.indexOf(COLON)) > -1) {
            this.readerUseVectors.skip(pos + 1);
            sum += pos;
        }
        this.readerUseVectors.reset();
        return sum;
    }
}
