package io.javafast.internal;

import io.javafast.http.HttpMethod;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;


/**
 * Benchmark                                                          Mode  Cnt    Score    Error   Units
 * RouterMatchBenchmark.compositeParamRouteMatch                      avgt    5  104.432 ±  0.862   ns/op
 * RouterMatchBenchmark.compositeParamRouteMatch:·gc.alloc.rate       avgt    5   ≈ 10⁻⁴           MB/sec
 * RouterMatchBenchmark.compositeParamRouteMatch:·gc.alloc.rate.norm  avgt    5   ≈ 10⁻⁵             B/op
 * RouterMatchBenchmark.compositeParamRouteMatch:·gc.count            avgt    5      ≈ 0           counts
 * RouterMatchBenchmark.nestedParamRouteMatch                         avgt    5   65.519 ±  0.421   ns/op
 * RouterMatchBenchmark.nestedParamRouteMatch:·gc.alloc.rate          avgt    5   ≈ 10⁻⁴           MB/sec
 * RouterMatchBenchmark.nestedParamRouteMatch:·gc.alloc.rate.norm     avgt    5   ≈ 10⁻⁶             B/op
 * RouterMatchBenchmark.nestedParamRouteMatch:·gc.count               avgt    5      ≈ 0           counts
 * RouterMatchBenchmark.paramRouteMatch                               avgt    5   35.659 ±  0.116   ns/op
 * RouterMatchBenchmark.paramRouteMatch:·gc.alloc.rate                avgt    5   ≈ 10⁻⁴           MB/sec
 * RouterMatchBenchmark.paramRouteMatch:·gc.alloc.rate.norm           avgt    5   ≈ 10⁻⁶             B/op
 * RouterMatchBenchmark.paramRouteMatch:·gc.count                     avgt    5      ≈ 0           counts
 * RouterMatchBenchmark.staticRouteMatch                              avgt    5   14.754 ±  0.095   ns/op
 * RouterMatchBenchmark.staticRouteMatch:·gc.alloc.rate               avgt    5   ≈ 10⁻⁴           MB/sec
 * RouterMatchBenchmark.staticRouteMatch:·gc.alloc.rate.norm          avgt    5   ≈ 10⁻⁶             B/op
 * RouterMatchBenchmark.staticRouteMatch:·gc.count                    avgt    5      ≈ 0           counts
 */
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
        this.router.add(HttpMethod.GET, "/file-{name}.txt", ctx -> {});
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

    @Benchmark
    public Object compositeParamRouteMatch() {
        return this.router.find(HttpMethod.GET, "/file-records.txt");
    }
}
