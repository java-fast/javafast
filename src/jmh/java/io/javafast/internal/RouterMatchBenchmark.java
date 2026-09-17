package io.javafast.internal;

import io.javafast.http.HttpMethod;
import org.openjdk.jmh.annotations.*;


// Benchmark                                                        Mode  Cnt          Score   Error   Units
// RouterMatchBenchmark.nestedParamRouteMatch                      thrpt         7378160.265           ops/s
// RouterMatchBenchmark.nestedParamRouteMatch:·gc.alloc.rate       thrpt            7092.555          MB/sec
// RouterMatchBenchmark.nestedParamRouteMatch:·gc.alloc.rate.norm  thrpt            1008.000            B/op
// RouterMatchBenchmark.nestedParamRouteMatch:·gc.count            thrpt             266.000          counts
// RouterMatchBenchmark.nestedParamRouteMatch:·gc.time             thrpt              70.000              ms

// RouterMatchBenchmark.paramRouteMatch                            thrpt        29794085.112           ops/s
// RouterMatchBenchmark.paramRouteMatch:·gc.alloc.rate             thrpt           14547.707          MB/sec
// RouterMatchBenchmark.paramRouteMatch:·gc.alloc.rate.norm        thrpt             512.000            B/op
// RouterMatchBenchmark.paramRouteMatch:·gc.count                  thrpt             453.000          counts
// RouterMatchBenchmark.paramRouteMatch:·gc.time                   thrpt             123.000              ms

// RouterMatchBenchmark.staticRouteMatch                           thrpt       122453461.318           ops/s
// RouterMatchBenchmark.staticRouteMatch:·gc.alloc.rate            thrpt            5620.750          MB/sec
// RouterMatchBenchmark.staticRouteMatch:·gc.alloc.rate.norm       thrpt              48.132            B/op
// RouterMatchBenchmark.staticRouteMatch:·gc.count                 thrpt             210.000          counts
// RouterMatchBenchmark.staticRouteMatch:·gc.time                  thrpt              56.000              ms
@State(Scope.Benchmark)
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
