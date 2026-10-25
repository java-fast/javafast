package io.javafast.internal;

import io.javafast.config.AppDataManager;
import io.javafast.transport.source.HttpRequestSource;
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
        private RequestContext context;
        private DefaultHttpRequestDispatcher dispatcher;

        @Setup(Level.Trial)
        public void setup() {
            this.buffer = TransportBuffer.create(128, 1);

            final DefaultRouting routing = new DefaultRouting();
            routing.get("/users/{id}", ctx -> {});

            final var pool = RequestContextPool.create(1);
            this.dispatcher = new DefaultHttpRequestDispatcher(pool, routing, new AppDataManager());
            this.writer = this.request.encode(this.buffer, "GET", "/users/42", null, null);
            final var context = pool.get();
            pool.release(context);
            this.context = context; // get context without lock
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            this.buffer.close();
        }
    }

    @State(Scope.Thread)
    public static class GetCompositeState {
        private final HttpRequestSource request = new HttpRequestSource();
        private TransportBuffer buffer;
        private TransportBuffer.Writer writer;
        private RequestContext context;
        private DefaultHttpRequestDispatcher dispatcher;

        @Setup(Level.Trial)
        public void setup() {
            this.buffer = TransportBuffer.create(128, 1);

            final DefaultRouting routing = new DefaultRouting();
            routing.get("/file-{name}.txt", ctx -> {});

            final var pool = RequestContextPool.create(1);
            this.dispatcher = new DefaultHttpRequestDispatcher(pool, routing, new AppDataManager());
            this.writer = this.request.encode(this.buffer, "GET", "/file-reports.txt", null, null);
            final var context = pool.get();
            pool.release(context);
            this.context = context; // get context without lock
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
        private RequestContext context;
        private DefaultHttpRequestDispatcher dispatcher;

        @Setup(Level.Trial)
        public void setup() {
            this.buffer = TransportBuffer.create(256, 1);

            final DefaultRouting routing = new DefaultRouting();
            routing.post("/users", ctx -> {});

            final var pool = RequestContextPool.create(1);
            this.dispatcher = new DefaultHttpRequestDispatcher(pool, routing, new AppDataManager());
            this.writer = this.request.encode(
                this.buffer, "POST", "/users", "{ \"name\":\"JavaFast\" }",
                Map.of("Content-Type", "application/json", "Content-Length", "21")
            );
            final var context = pool.get();
            pool.release(context);
            this.context = context; // get context without lock
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            this.buffer.close();
        }
    }

    @State(Scope.Thread)
    public static class NotFoundState {
        private final HttpRequestSource request = new HttpRequestSource();
        private TransportBuffer buffer;
        private TransportBuffer.Writer writer;
        private RequestContext context;
        private DefaultHttpRequestDispatcher dispatcher;

        @Setup(Level.Trial)
        public void setup() {
            this.buffer = TransportBuffer.create(128, 1);

            final DefaultRouting routing = new DefaultRouting();
            routing.get("/users", ctx -> {});

            final var pool = RequestContextPool.create(1);
            this.dispatcher = new DefaultHttpRequestDispatcher(pool, routing, new AppDataManager());
            this.writer = this.request.encode(this.buffer, "GET", "/boo", null, null);
            final var context = pool.get();
            pool.release(context);
            this.context = context; // get context without lock
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            this.buffer.close();
        }
    }

    @Benchmark
    public int dispatchGet(GetState state) throws Exception {
        state.dispatcher.dispatch(state.writer);
        return state.context.statusCode();
    }

    @Benchmark
    public int dispatchGetComposite(GetCompositeState state) throws Exception {
        state.dispatcher.dispatch(state.writer);
        return state.context.statusCode();
    }

    @Benchmark
    public int dispatchPost(PostState state) throws Exception {
        state.dispatcher.dispatch(state.writer);
        return state.context.statusCode();
    }

    @Benchmark
    public int dispatchNotFound(NotFoundState state) throws Exception {
        state.dispatcher.dispatch(state.writer);
        return state.context.statusCode();
    }

}
