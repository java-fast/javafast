package io.javafast.internal;

import io.javafast.config.AppData;
import io.javafast.http.*;
import io.javafast.json.JsonMapper;
import io.javafast.plugin.ContextPlugin;
import io.javafast.transport.HttpRequestDecoder;
import io.javafast.transport.TransportBuffer;
import io.javafast.validation.BodyValidator;
import io.javafast.validation.Validator;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Internal request context
 */
final class RequestContext implements Context {

    // TODO can be singleton!
    private final HttpRequestDecoder decoder = new HttpRequestDecoder();

    private long sequence;
    private TransportBuffer.Reader reader;
    private AppData appData;

    private HttpMethod method;
    final ByteString path = new ByteString(32);

    private String queryString;

    private String contextPath;
    private PathParams pathParamMap;
    private Map<String, String> headerMap;

    private int statusCode = HttpStatus.OK.code();
    private String result;
    private InputStream resultInputStream;

    private boolean skipRemainingHandlers = false;

    static String header(TransportBuffer.Reader block, String header) {
        // TODO read header from block
        return "";
    }

    static InputStream requestBody(TransportBuffer.Reader block) {
        // TODO read request body from block
        return InputStream.nullInputStream();
    }

    static OutputStream responseBody(TransportBuffer.Reader block) {
        // TODO write response body to block or socket?
        return OutputStream.nullOutputStream();
    }

    RequestContext() {
        // this.method = HttpMethod.of(exchange.method());
        /*
        final String uri = exchange.uri();
        final int queryIndex = uri.indexOf('?');
        if (queryIndex >= 0) {
            this.path = uri.substring(0, queryIndex);
            this.queryString = uri.substring(queryIndex + 1);
        } else {
            this.path = uri;
            this.queryString = "";
        }
        */
    }

    public void reset(long sequence, TransportBuffer.Reader reader, AppData appData) {
        this.sequence = sequence;
        this.reader = reader;
        this.appData = appData;
        this.method = null;
        this.queryString = null;
        this.contextPath = null;
        this.pathParamMap = null;
        this.headerMap = null;
        this.result = null;
        this.resultInputStream = null;
        this.skipRemainingHandlers = false;

        this.decoder.parse(reader);
        this.decoder.setTarget(this.path);
    }

    @Override
    public int contentLength() {
        return 0;
    }

    @Override
    public String contentType() {
        return "";
    }

    @Override
    public HttpMethod method() {
        if (this.method == null) {
            this.method = this.decoder.getMethod();
        }
        return this.method;
    }

    @Override
    public String path() {
        return this.path.toString();
    }

    @Override
    public int port() {
        return 0;
    }

    @Override
    public String protocol() {
        return "";
    }

    @Override
    public String contextPath() {
        return this.contextPath;
    }

    @Override
    public String userAgent() {
        return "";
    }

    @Override
    public String characterEncoding() {
        return "";
    }

    @Override
    public String url() {
        return "";
    }

    @Override
    public String fullUrl() {
        return "";
    }

    @Override
    public String scheme() {
        return "";
    }

    @Override
    public String host() {
        return "";
    }

    @Override
    public String ip() {
        return "";
    }

    @Override
    public String queryString() {
        return this.queryString;
    }

    @Override
    public <T> T appData(String key) {
        return this.appData.get(key);
    }

    @Override
    public JsonMapper jsonMapper() {
        return null;
    }

    @Override
    public <T> T with(Class<? extends ContextPlugin<?, T>> pluginClass) {
        return null;
    }

    @Override
    public String body() {
        return new String(bodyAsBytes(), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] bodyAsBytes() {
        // TODO optimize later via reusing a buffer byte[]
        try (final var in = requestBody(this.reader)){
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public <T> T bodyAsClass(Type type) {
        return null;
    }

    @Override
    public <T> T bodyAsClass(Class<T> type) {
        return null;
    }

    @Override
    public <T> T bodyStreamAsClass(Type type) {
        return null;
    }

    @Override
    public InputStream bodyInputStream() {
        return null;
    }

    @Override
    public <T> BodyValidator<T> bodyValidator(Class<T> type) {
        return null;
    }

    @Override
    public String formParam(String key) {
        return "";
    }

    @Override
    public <T> Validator<T> formParamAsClass(String key, Class<T> type) {
        return null;
    }

    @Override
    public List<String> formParams(String key) {
        return List.of();
    }

    @Override
    public <T> Validator<List<T>> formParamsAsClass(String key, Class<T> type) {
        return null;
    }

    @Override
    public Map<String, List<String>> formParamMap() {
        return Map.of();
    }

    @Override
    public String pathParam(int index) {
        return this.pathParamMap.get(index);
    }

    @Override
    public String pathParam(String key) {
        return this.pathParamMap.get(key);
    }

    @Override
    public <T> Validator<T> pathParamAsClass(String key, Class<T> type) {
        return null;
    }

    @Override
    public Map<String, String> pathParamMap() {
        return this.pathParamMap.get();
    }

    @Override
    public String queryParam(String key) {
        return "";
    }

    @Override
    public <T> Validator<T> queryParamAsClass(String key, Class<T> type) {
        return null;
    }

    @Override
    public List<String> queryParams(String key) {
        return List.of();
    }

    @Override
    public <T> Validator<List<T>> queryParamsAsClass(String key, Class<T> type) {
        return null;
    }

    @Override
    public Map<String, List<String>> queryParamMap() {
        return Map.of();
    }

    @Override
    public void attribute(String key, Object value) {

    }

    @Override
    public <T> T attribute(String key) {
        return null;
    }

    @Override
    public <T> T attributeOrCompute(String key, Function<Context, T> callback) {
        return null;
    }

    @Override
    public Map<String, Object> attributeMap() {
        return Map.of();
    }

    @Override
    public String header(String name) {
        if (this.headerMap != null && this.headerMap.containsKey(name)) {
            return this.headerMap.get(name);
        }
        return header(this.reader, name);
    }

    @Override
    public <T> Validator<T> headerAsClass(String name, Class<T> type) {
        return null;
    }

    @Override
    public Map<String, String> headerMap() {
        return this.headerMap;
    }

    @Override
    public String cookie(String name) {
        return "";
    }

    @Override
    public Map<String, String> cookieMap() {
        return Map.of();
    }

    @Override
    public String basicAuthCredentials() {
        return "";
    }

    @Override
    public boolean isMultipart() {
        return false;
    }

    @Override
    public boolean isMultipartFormData() {
        return false;
    }

    @Override
    public boolean isFormUrlencoded() {
        return false;
    }

    @Override
    public boolean isJson() {
        return false;
    }

    @Override
    public UploadedFile uploadedFile(String name) {
        return null;
    }

    @Override
    public List<UploadedFile> uploadedFiles(String name) {
        return List.of();
    }

    @Override
    public List<UploadedFile> uploadedFiles() {
        return List.of();
    }

    @Override
    public Map<String, List<UploadedFile>> uploadedFileMap() {
        return Map.of();
    }

    @Override
    public Charset responseCharset() {
        return null;
    }

    @Override
    public OutputStream outputStream() {
        return responseBody(this.reader);
    }

    @Override
    public Context result(String value) {
        this.result = value;
        this.resultInputStream = new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    @Override
    public Context result(byte[] bytes) {
        this.result = new String(bytes, StandardCharsets.UTF_8);
        this.resultInputStream = new ByteArrayInputStream(bytes);
        return this;
    }

    @Override
    public Context result(InputStream stream) {
        this.resultInputStream = stream;
        this.result = "";
        return this;
    }

    @Override
    public String result() {
        return this.result;
    }

    @Override
    public InputStream resultInputStream() {
        return Objects.requireNonNullElseGet(this.resultInputStream, () -> new ByteArrayInputStream(new byte[0]));
    }

    @Override
    public Context contentType(String contentType) {
        return null;
    }

    @Override
    public Context header(String name, String value) {
        if (this.headerMap == null) {
            this.headerMap = new HashMap<>();
        }
        this.headerMap.put(name, value);
        return this;
    }

    @Override
    public Context addHeader(String name, String value) {
        // TODO multi-value headers?
        if (this.headerMap == null) {
            this.headerMap = new HashMap<>();
        }
        this.headerMap.put(name, value);
        return this;
    }

    @Override
    public Context removeHeader(String name) {
        if (this.headerMap != null) {
            this.headerMap.remove(name);
        }
        return this;
    }

    @Override
    public Context redirect(String location, HttpStatus status) {
        return null;
    }

    @Override
    public Context status(HttpStatus status) {
        this.statusCode = status.code();
        return this;
    }

    @Override
    public Context status(int status) {
        this.statusCode = status;
        return this;
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.forStatus(this.statusCode);
    }

    @Override
    public int statusCode() {
        return this.statusCode;
    }

    @Override
    public Context cookie(String name, String value) {
        return null;
    }

    @Override
    public Context cookie(String name, String value, int maxAge) {
        return null;
    }

    @Override
    public Context cookie(Cookie cookie) {
        return null;
    }

    @Override
    public Context removeCookie(String name, String path) {
        return null;
    }

    @Override
    public Context json(Object object, Type type) {
        return null;
    }

    @Override
    public Context jsonStream(Object object, Type type) {
        return null;
    }

    @Override
    public void writeJsonStream(Stream<?> stream) {

    }

    @Override
    public Context skipRemainingHandlers() {
        this.skipRemainingHandlers = true;
        return this;
    }

    boolean isSkipRemainingHandlers() {
        return this.skipRemainingHandlers;
    }

    void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    void setPathParamMap(PathParams pathParamMap) {
        this.pathParamMap = pathParamMap;
    }

    void writeResponse() throws Exception {
        if (this.reader == null) {
            return;
        }
        if (this.headerMap != null) { // write response headers
            // TODO write headers to response
        }
        // this.exchange.status(this.statusCode);
        if(this.method == HttpMethod.HEAD) {
            return; // no body for HEAD
        }
//        final var stream = resultInputStream();
//        try (stream; final var out = responseBody(this.reader)) {
//            stream.transferTo(out);
//        }
    }

}
