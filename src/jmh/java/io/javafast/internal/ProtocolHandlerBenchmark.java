package io.javafast.internal;

import io.javafast.transport.HttpProtocolHandler;
import io.javafast.transport.TransportBuffer;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ProtocolHandlerBenchmark {

    @State(Scope.Thread)
    public static class GetState {
        private final byte[] requestBytes = "GET /users/42 HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes();
        private TransportBuffer buffer;
        private TransportBuffer.Writer writer;
        private HttpProtocolHandler handler;

        @Setup(Level.Trial)
        public void setup() {
            this.buffer = TransportBuffer.create(128, 1);
            this.writer = this.buffer.writer();
            this.handler = new HttpProtocolHandler();
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            this.buffer.close();
        }
    }

    @State(Scope.Thread)
    public static class PostState {
        private final byte[] requestBytes = "POST /users HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: 21\r\n\r\n{ \"name\":\"JavaFast\" }".getBytes();
        private TransportBuffer buffer;
        private TransportBuffer.Writer writer;
        private HttpProtocolHandler handler;

        @Setup(Level.Trial)
        public void setup() {
            this.buffer = TransportBuffer.create(256, 1);
            this.writer = this.buffer.writer();
            this.handler = new HttpProtocolHandler();
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            this.buffer.close();
        }
    }

    @Benchmark
    public boolean handleGet(GetState state) {
        state.handler.reset();
        state.writer.reset();
        state.writer.writeBytes(state.requestBytes);
        state.handler.onBytes(state.writer.reader(), state.writer.totalWritten());
        return state.handler.payloadComplete();
    }

    @Benchmark
    public boolean handlePost(PostState state) {
        state.handler.reset();
        state.writer.reset();
        state.writer.writeBytes(state.requestBytes);
        state.handler.onBytes(state.writer.reader(), state.writer.totalWritten());
        return state.handler.payloadComplete();
    }

    static void main() {
        final ProtocolHandlerBenchmark benchmark = new ProtocolHandlerBenchmark();
        final GetState state = new GetState();
        state.setup();
        System.out.println(benchmark.handleGet(state));

        final PostState state2 = new PostState();
        state2.setup();
        System.out.println(benchmark.handlePost(state2));
    }
}
