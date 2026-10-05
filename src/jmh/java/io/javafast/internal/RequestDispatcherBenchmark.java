package io.javafast.internal;

import io.javafast.config.AppDataManager;
import io.javafast.transport.HttpRequestSource;
import io.javafast.transport.TransportBuffer;
import org.openjdk.jmh.annotations.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class RequestDispatcherBenchmark {

    @State(Scope.Thread)
    public static class GetState {
        private final HttpRequestSource request = new HttpRequestSource();
        private TransportBuffer buffer;
        private TransportBuffer.Writer writer;
        private RequestContextPool pool;
        private DefaultHttpRequestDispatcher dispatcher;

        @Setup(Level.Trial)
        public void setup() {
            this.buffer = TransportBuffer.create(128, 1);

            final DefaultRouting routing = new DefaultRouting();
            routing.get("/users/{id}", ctx -> {});

            this.pool = RequestContextPool.create(1);
            this.dispatcher = new DefaultHttpRequestDispatcher(this.pool, routing, new AppDataManager());
            this.writer = this.request.encode(this.buffer, "GET", "/users/42", null, null);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            this.buffer.close();
        }
    }

    @State(Scope.Thread)
    public static class PostState {
        private final HttpRequestSource request = new HttpRequestSource();
        private TransportBuffer buffer;
        private TransportBuffer.Writer writer;
        private RequestContextPool pool;
        private DefaultHttpRequestDispatcher dispatcher;

        @Setup(Level.Trial)
        public void setup() {
            this.buffer = TransportBuffer.create(256, 1);

            final DefaultRouting routing = new DefaultRouting();
            routing.post("/users", ctx -> {});

            this.pool = RequestContextPool.create(1);
            this.dispatcher = new DefaultHttpRequestDispatcher(this.pool, routing, new AppDataManager());
            this.writer = this.request.encode(
                this.buffer, "POST", "/users", "{ \"name\":\"JavaFast\" }",
                Map.of("Content-Type", "application/json", "Content-Length", "21")
            );
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            this.buffer.close();
        }
    }

    @Benchmark
    public int dispatchGet(GetState state) throws Exception {
        state.dispatcher.dispatch(state.writer.reader());
        return state.pool.get().statusCode();
    }

    @Benchmark
    public int dispatchPost(PostState state) throws Exception {
        state.dispatcher.dispatch(state.writer.reader());
        return state.pool.get().statusCode();
    }

}
