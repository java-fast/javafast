package io.javafast.config;

import io.javafast.http.Context;

import java.util.function.Function;

public class ContextResolverConfig {

    public Function<Context, String> host;
    public Function<Context, String> scheme;
    public Function<Context, String> url;
    public Function<Context, String> fullUrl;

}
