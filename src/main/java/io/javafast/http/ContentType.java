package io.javafast.http;

/**
 * Common HTTP media-type constants. Media types remain open-ended strings.
 */
public final class ContentType {
    private ContentType() {}

    public static final String PLAIN = "text/plain";
    public static final String CSS = "text/css";
    public static final String HTML = "text/html";
    public static final String XML = "text/xml";
    public static final String JAVASCRIPT = "text/javascript";
    public static final String JSON = "application/json";
    public static final String PROBLEM_JSON = "application/problem+json";
    public static final String PROBLEM_XML = "application/problem+xml";
    public static final String FORM_DATA = "multipart/form-data";
    public static final String OCTET_STREAM = "application/octet-stream";
}
