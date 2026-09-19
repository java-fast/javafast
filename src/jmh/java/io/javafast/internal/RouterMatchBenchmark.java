package io.javafast.internal;

import io.javafast.http.HttpMethod;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;


/*
* Benchmark                                                       Mode  Cnt   Score     Error   Units
* RouterMatchBenchmark.nestedParamRouteMatch                      avgt    5  65.733 ±   0.327   ns/op
* RouterMatchBenchmark.nestedParamRouteMatch:·gc.alloc.rate       avgt    5  ≈ 10⁻⁴            MB/sec
* RouterMatchBenchmark.nestedParamRouteMatch:·gc.alloc.rate.norm  avgt    5  ≈ 10⁻⁶              B/op
* RouterMatchBenchmark.nestedParamRouteMatch:·gc.count            avgt    5     ≈ 0            counts
* RouterMatchBenchmark.paramRouteMatch                            avgt    5  30.005 ±   0.230   ns/op
* RouterMatchBenchmark.paramRouteMatch:·gc.alloc.rate             avgt    5  ≈ 10⁻⁴            MB/sec
* RouterMatchBenchmark.paramRouteMatch:·gc.alloc.rate.norm        avgt    5  ≈ 10⁻⁶              B/op
* RouterMatchBenchmark.paramRouteMatch:·gc.count                  avgt    5     ≈ 0            counts
* RouterMatchBenchmark.staticRouteMatch                           avgt    5  14.739 ±   0.093   ns/op
* RouterMatchBenchmark.staticRouteMatch:·gc.alloc.rate            avgt    5  ≈ 10⁻⁴            MB/sec
* RouterMatchBenchmark.staticRouteMatch:·gc.alloc.rate.norm       avgt    5  ≈ 10⁻⁶              B/op
* RouterMatchBenchmark.staticRouteMatch:·gc.count                 avgt    5     ≈ 0            counts
* */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class RouterMatchBenchmark {

    private Router router;

    @Setup
    public void setup() {
        this.router = new Router();
        this.router.add(HttpMethod.GET, "/health", ctx -> {});
        this.router.add(HttpMethod.GET, "/users/{id}", ctx -> {});
        this.router.add(HttpMethod.GET, "/teams/{teamId}/users/{userId}", ctx -> {});
    }

    @Benchmark
    public Object staticRouteMatch() {
        return this.router.find(HttpMethod.GET, "/health");
    }

    @Benchmark
    public Object paramRouteMatch() {
        return this.router.find(HttpMethod.GET, "/users/42");
    }

    @Benchmark
    public Object nestedParamRouteMatch() {
        return this.router.find(HttpMethod.GET, "/teams/7/users/42");
    }
}
