package io.javafast.internal;

import io.javafast.config.AppDataManager;
import io.javafast.transport.TransportBuffer;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class RequestDispatcherBenchmark {

    @State(Scope.Thread)
    public static class GetState {
        private final byte[] requestBytes = "GET /users/42 HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes();
        private TransportBuffer buffer;
        private TransportBuffer.Writer writer;
        private RequestContext context;
        private DefaultHttpRequestDispatcher dispatcher;

        @Setup(Level.Trial)
        public void setup() {
            this.buffer = TransportBuffer.create(128, 1);

            final DefaultRouting routing = new DefaultRouting();
            routing.get("/users/{id}", ctx -> {});

            this.writer = this.buffer.writer();
            final var pool = RequestContextPool.create(1);
            this.dispatcher = new DefaultHttpRequestDispatcher(pool, routing, new AppDataManager());
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
        private final byte[] requestBytes = "GET /file-reports.txt HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes();
        private TransportBuffer buffer;
        private TransportBuffer.Writer writer;
        private RequestContext context;
        private DefaultHttpRequestDispatcher dispatcher;

        @Setup(Level.Trial)
        public void setup() {
            this.buffer = TransportBuffer.create(128, 1);

            final DefaultRouting routing = new DefaultRouting();
            routing.get("/file-{name}.txt", ctx -> {});

            this.writer = this.buffer.writer();
            final var pool = RequestContextPool.create(1);
            this.dispatcher = new DefaultHttpRequestDispatcher(pool, routing, new AppDataManager());
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
        private final byte[] requestBytes = "POST /users HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: 21\r\n\r\n{ \"name\":\"JavaFast\" }".getBytes();
        private TransportBuffer buffer;
        private TransportBuffer.Writer writer;
        private RequestContext context;
        private DefaultHttpRequestDispatcher dispatcher;

        @Setup(Level.Trial)
        public void setup() {
            this.buffer = TransportBuffer.create(256, 1);

            final DefaultRouting routing = new DefaultRouting();
            routing.post("/users", ctx -> {});

            this.writer = this.buffer.writer();
            final var pool = RequestContextPool.create(1);
            this.dispatcher = new DefaultHttpRequestDispatcher(pool, routing, new AppDataManager());
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
        private final byte[] requestBytes = "GET /boo HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes();
        private TransportBuffer buffer;
        private TransportBuffer.Writer writer;
        private RequestContext context;
        private DefaultHttpRequestDispatcher dispatcher;

        @Setup(Level.Trial)
        public void setup() {
            this.buffer = TransportBuffer.create(128, 1);

            final DefaultRouting routing = new DefaultRouting();
            routing.get("/users", ctx -> {});

            this.writer = this.buffer.writer();
            final var pool = RequestContextPool.create(1);
            this.dispatcher = new DefaultHttpRequestDispatcher(pool, routing, new AppDataManager());
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
        state.writer.reset();
        state.writer.writeBytes(state.requestBytes);
        state.dispatcher.dispatch(state.writer);
        return state.context.statusCode();
    }

    @Benchmark
    public int dispatchGetComposite(GetCompositeState state) throws Exception {
        state.writer.reset();
        state.writer.writeBytes(state.requestBytes);
        state.dispatcher.dispatch(state.writer);
        return state.context.statusCode();
    }

    @Benchmark
    public int dispatchPost(PostState state) throws Exception {
        state.writer.reset();
        state.writer.writeBytes(state.requestBytes);
        state.dispatcher.dispatch(state.writer);
        return state.context.statusCode();
    }

    @Benchmark
    public int dispatchNotFound(NotFoundState state) throws Exception {
        state.writer.reset();
        state.writer.writeBytes(state.requestBytes);
        state.dispatcher.dispatch(state.writer);
        return state.context.statusCode();
    }

}
