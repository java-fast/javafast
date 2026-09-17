package io.javafast.internal;

import io.javafast.http.HttpMethod;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;


//Benchmark                                                       Mode  Cnt     Score   Error   Units
//RouterMatchBenchmark.nestedParamRouteMatch                      avgt        157.288           ns/op
//RouterMatchBenchmark.nestedParamRouteMatch:·gc.alloc.rate       avgt       6645.218          MB/sec
//RouterMatchBenchmark.nestedParamRouteMatch:·gc.alloc.rate.norm  avgt       1096.000            B/op
//RouterMatchBenchmark.nestedParamRouteMatch:·gc.count            avgt        250.000          counts
//RouterMatchBenchmark.nestedParamRouteMatch:·gc.time             avgt         80.000              ms
//RouterMatchBenchmark.paramRouteMatch                            avgt         70.126           ns/op
//RouterMatchBenchmark.paramRouteMatch:·gc.alloc.rate             avgt       7180.342          MB/sec
//RouterMatchBenchmark.paramRouteMatch:·gc.alloc.rate.norm        avgt        528.000            B/op
//RouterMatchBenchmark.paramRouteMatch:·gc.count                  avgt        266.000          counts
//RouterMatchBenchmark.paramRouteMatch:·gc.time                   avgt         82.000              ms
//RouterMatchBenchmark.staticRouteMatch                           avgt         20.224           ns/op
//RouterMatchBenchmark.staticRouteMatch:·gc.alloc.rate            avgt       6790.237          MB/sec
//RouterMatchBenchmark.staticRouteMatch:·gc.alloc.rate.norm       avgt        144.000            B/op
//RouterMatchBenchmark.staticRouteMatch:·gc.count                 avgt        254.000          counts
//RouterMatchBenchmark.staticRouteMatch:·gc.time                  avgt         78.000              ms
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
