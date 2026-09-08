package io.javafast.http;

/**
 * Common HTTP header names. Unknown/vendor headers should be passed as strings.
 */
public final class Header {
    private Header() {
    }

    public static final String ACCEPT = "Accept";
    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    public static final String AUTHORIZATION = "Authorization";
    public static final String CACHE_CONTROL = "Cache-Control";
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String COOKIE = "Cookie";
    public static final String HOST = "Host";
    public static final String LOCATION = "Location";
    public static final String ORIGIN = "Origin";
    public static final String REFERER = "Referer";
    public static final String SET_COOKIE = "Set-Cookie";
    public static final String USER_AGENT = "User-Agent";
    public static final String X_HTTP_METHOD_OVERRIDE = "X-HTTP-Method-Override";
}
