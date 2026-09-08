package io.javafast.config;

import io.javafast.http.ContentType;

public class HttpConfig {
    public HttpConfig() {}
    public boolean generateEtags;
    public boolean prefer405over404;
    public boolean strictContentTypes;
    public Object maxRequestSize;
    public int responseBufferSize;
    public ContentType defaultContentType;
    public long asyncTimeout;
}
