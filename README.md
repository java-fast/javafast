# JavaFast

**High-performance Java HTTP server with minimal runtime overhead**

A lightweight HTTP server built entirely on standard JDK APIs, designed for competitive throughput, near-zero allocation in the hot path, and predictable GC behavior.

JavaFast combines a simple, synchronous-looking programming model with asynchronous, non-blocking I/O and efficient request processing:

```java
void main() {
    JavaFast.create(config -> {
        config.routes.get("/", ctx -> ctx.result("Hello World"));
    }).start(8080);
}
```

## Core Principles

- **Simple API:** provide a straightforward programming model without exposing transport-level complexity.
- **Virtual Threads by heart:** use lightweight virtual threads for request/response handling. No reactive programming is needed.
- **Low allocation overhead:** minimize unnecessary allocations in request processing and zero-copy routing.
- **Efficient I/O:** use non-blocking socket I/O for connection handling.
- **High throughput:** optimize the request-processing path through benchmarking and JVM-level tuning.

## Why JavaFast?

1. High performance, low overhead
- Competitive throughput.
- Almost-zero allocation in the hot path.
- Minimal GC pressure.
- Stable latency under sustained load.
- Predictable memory consumption.
  
2. Pure JDK, zero external dependencies
- No Netty.
- No custom native transport.
- No external networking libraries.
- Standard Java NIO.
- Standard JDK concurrency primitives.
- Easy JDK upgrades.

3. Maintainability and operational simplicity
- No native library deployment.
- No platform-specific transport configuration.
- Standard JVM monitoring and profiling.
- Straightforward debugging with JFR.
- Easy integration into existing Java applications.

**JavaFast's philosophy:**

Let the JDK handle the platform-specific complexity. Optimize everything around it.

## Coming from Javalin?

JavaFast uses a familiar, Javalin-style routing API. Most applications should require only minimal changes to get started.

## Status

JavaFast is an experimental project focused on HTTP server architecture, JVM performance, and efficient resource management. It is not production ready yet, and HTTP specifications are not fully supported currently.

More documentation, benchmarks, and examples will follow as the project evolves.
