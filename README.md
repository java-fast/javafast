# JavaFast

**A high-performance, low-allocation HTTP server framework for modern Java.**

JavaFast combines a simple, synchronous-looking programming model with asynchronous, non-blocking I/O and efficient request processing.

```java
void main() {
    JavaFast.create(config -> {
        config.routes.get("/", ctx -> ctx.result("Hello World"));
    }).start(8080);
}
```

## Design Goals

- **Low allocation overhead:** minimize unnecessary allocations in request processing and zero-copy routing.
- **Efficient I/O:** use non-blocking socket I/O for connection handling.
- **High throughput:** optimize the request-processing path through benchmarking and JVM-level tuning.
- **Simple API:** provide a straightforward programming model without exposing transport-level complexity.

## Coming from Javalin?

JavaFast uses a familiar, Javalin-style routing API. Most applications should require only minimal changes to get started.

## Status

JavaFast is an experimental project focused on HTTP server architecture, JVM performance, and efficient resource management. It is not production ready yet.

More documentation, benchmarks, and examples will follow as the project evolves.
