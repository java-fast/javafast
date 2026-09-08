package io.javafast.event;

import io.javafast.http.Handler;
import io.javafast.http.HttpMethod;

import java.util.Set;

public record HandlerMetaInfo(HttpMethod httpMethod, String path, Handler handler, Set<String> roles) {
}
