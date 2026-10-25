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
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Internal request context
 */
final class RequestContext implements Context {

    private static final byte CR = '\r';
    private static final byte LF = '\n';
    private static final byte COLON = ':';
    private static final byte SPACE = ' ';
    private static final byte[] HTTP_1_1_ = "HTTP/1.1 ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CONTENT_LENGTH_ = "Content-Length: ".getBytes(StandardCharsets.US_ASCII);

    private final HttpRequestDecoder decoder = new HttpRequestDecoder();

    private TransportBuffer.Writer writer;
    private TransportBuffer.Reader reader;

    private boolean writingResponse;
    private boolean skipRemainingHandlers;

    private AppData appData;
    private String contextPath;

    private HttpMethod method;
    private String path;
    private String queryString;
    private PathParams pathParamMap;
    private Map<String, String> requestHeaders;

    private int statusCode = HttpStatus.OK.code();
    private Map<String, String> responseHeaders;
    private String responseBody;
    private byte[] responseBodyBytes;
    private InputStream responseBodyInputStream;
    private int responseContentLength;
    private final TransportBufferOutputStream responseBodyOutputStream = new TransportBufferOutputStream();

    final RouterState routerState = new RouterState(); // re-used by the dispatcher
    final AtomicBoolean occupied = new AtomicBoolean(); // CAS set by VTs

    private void ensureRequestReadable() {
        if (this.writingResponse) {
            throw RequestConsumedException.get();
        }
    }

    private String lookupHeader(String header) {
        final int count = this.decoder.getHeadersCount();
        if (count == 0) return null;
        for (int i = 0; i < count; i++) {
            if (this.decoder.matchHeaderName(i, header)) {
                if (this.requestHeaders != null) {
                    this.requestHeaders = new HashMap<>();
                }
                final String value = this.decoder.getHeader(i);
                this.requestHeaders.put(header, value);
                return value;
            }
        }
        return null;
    }

    private Map<String, String> getHeaders() {
        final int count = this.decoder.getHeadersCount();
        if (count == 0) return Collections.emptyMap();
        ensureRequestReadable();
        if (this.requestHeaders == null) {
            this.requestHeaders = new HashMap<>();
        }
        this.requestHeaders.clear();
        for (int i = 0; i < count; i++) {
            final String name = this.decoder.getHeaderName(i);
            final String value = this.decoder.getHeader(i);
            this.requestHeaders.put(name, value);
        }
        return this.requestHeaders;
    }

    void reset(TransportBuffer.Writer writer, AppData appData, String contextPath) {
        this.writer = writer;
        this.reader = writer.reader();
        this.appData = appData;
        this.contextPath = contextPath;

        this.writingResponse = false;
        this.skipRemainingHandlers = false;

        this.method = null;
        this.path = null;
        this.queryString = null;
        this.pathParamMap = null;
        this.requestHeaders = null;
        this.responseBody = null;
        this.responseBodyInputStream = null;
        this.responseHeaders = null;

        this.reader.reset();
        this.decoder.parse(this.reader);
    }

    @Override
    public int contentLength() {
        return this.decoder.getContentLength();
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
        if (this.path == null) {
            final int index = getTarget().indexOf('?');
            if (index == -1) {
                this.path = getTarget().toString();
            } else {
                this.path = getTarget().substring(0, index);
            }
        }
        return this.path;
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
        // TODO optimize
        return "http://" + header("Host") + "/" + path();
    }

    @Override
    public String fullUrl() {
        // TODO optimize
        return "http://" + header("Host") + "/" + path() + "?" + queryString();
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
        if (this.queryString == null) {
            final int index = getTarget().indexOf('?');
            if (index == -1) {
                this.queryString = "";
            } else {
                this.queryString = getTarget().substring(index + 1, getTarget().length());
            }
        }
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
        ensureRequestReadable();
        return this.decoder.getBodyAsString();
    }

    @Override
    public byte[] bodyAsBytes() {
        ensureRequestReadable();
        return this.decoder.getBody();
    }

    @Override
    public <T> T bodyAsClass(Type type) {
        ensureRequestReadable();
        // TODO json mapping to the type
        return null;
    }

    @Override
    public <T> T bodyAsClass(Class<T> type) {
        ensureRequestReadable();
        // TODO json mapping to the type
        return null;
    }

    @Override
    public <T> T bodyStreamAsClass(Type type) {
        ensureRequestReadable();
        // TODO json mapping to the type
        return null;
    }

    @Override
    public InputStream bodyInputStream() {
        ensureRequestReadable();
        // TODO request body input stream, maybe TransportBufferInputStream?
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
    public String header(int index) {
        if (index >= this.decoder.getHeadersCount()) {
            return null;
        }
        ensureRequestReadable();
        return this.decoder.getHeader(index);
    }

    @Override
    public String header(String name) {
        if (this.requestHeaders != null && this.requestHeaders.containsKey(name)) {
            return this.requestHeaders.get(name);
        }
        ensureRequestReadable();
        return lookupHeader(name);
    }

    @Override
    public <T> Validator<T> headerAsClass(String name, Class<T> type) {
        ensureRequestReadable();
        // TODO json mapping to the type
        return null;
    }

    @Override
    public Map<String, String> headerMap() {
        return Map.copyOf(getHeaders()); // copy protection
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
        this.writingResponse = true;
        // TODO calculate line + headers offset
        // TODO return the body slice of TransportBufferOutputStream
        return null;
    }

    @Override
    public Context result(String value) {
        this.responseBody = value;
        this.responseBodyBytes = null;
        this.responseBodyInputStream = null;
        this.responseContentLength = TransportBuffer.Writer.utf8Length(value);
        return this;
    }

    @Override
    public Context result(byte[] bytes) {
        this.responseBody = null;
        this.responseBodyBytes = bytes; // unsafe, no copy
        this.responseBodyInputStream = null;
        this.responseContentLength = bytes.length;
        return this;
    }

    @Override
    public Context result(InputStream stream) {
        this.responseBody = null;
        this.responseBodyBytes = null;
        this.responseBodyInputStream = stream;
        this.responseContentLength = -1; // will be set while writing
        return this;
    }

    @Override
    public String result() {
        return this.responseBody;
    }

    @Override
    public InputStream resultInputStream() {
        return this.responseBodyInputStream;
    }

    @Override
    public Context contentType(String contentType) {
        if (this.responseHeaders == null) {
            this.responseHeaders = new HashMap<>(); // TODO a custom faster map
        }
        this.responseHeaders.put("Content-Type", contentType);
        return null;
    }

    @Override
    public Context header(String name, String value) {
        if (this.responseHeaders == null) {
            this.responseHeaders = new HashMap<>();
        }
        this.responseHeaders.put(name, value);
        return this;
    }

    @Override
    public Context addHeader(String name, String value) {
        // TODO handle multi-value headers?
        if (this.responseHeaders == null) {
            this.responseHeaders = new HashMap<>();
        }
        this.responseHeaders.put(name, value);
        return this;
    }

    @Override
    public Context removeHeader(String name) {
        if (this.responseHeaders != null) {
            this.responseHeaders.remove(name);
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

    ByteString getTarget() {
        return this.decoder.getTarget();
    }

    boolean isSkipRemainingHandlers() {
        return this.skipRemainingHandlers;
    }

    void setPathParamMap(PathParams pathParamMap) {
        this.pathParamMap = pathParamMap;
    }

    void writeResponse() throws Exception {
        if (this.writer == null) {
            return;
        }
        this.writingResponse = true;
        this.reader.reset();
        this.responseBodyOutputStream.reset(this.writer);
        // write status line
        this.writer.writeBytes(HTTP_1_1_);
        this.writer.writeBytes(status().getWireBytes());
        this.writer.writeByte(CR);
        this.writer.writeByte(LF);
        if (this.responseHeaders != null) { // write response headers
            this.responseHeaders.forEach((key, value) -> {
                this.writer.writeAscii(key);
                this.writer.writeByte(COLON);
                this.writer.writeByte(SPACE);
                this.writer.writeAscii(value);
                this.writer.writeByte(CR);
                this.writer.writeByte(LF);
            });
        }
        if (this.method == HttpMethod.HEAD
            || this.statusCode == 204
            || this.statusCode == 304
        ) { // empty body cases
            this.writer.writeByte(CR);
            this.writer.writeByte(LF);
            return;
        }
        if (this.responseContentLength > -1) {
            this.writer.writeBytes(CONTENT_LENGTH_);
            this.writer.writeIntAscii(this.responseContentLength);
            this.writer.writeByte(CR);
            this.writer.writeByte(LF);
        } else {
            // TODO write chunked
        }
        this.writer.writeByte(CR);
        this.writer.writeByte(LF);
        if (this.responseBody != null) {
            this.writer.writeUtf8(this.responseBody);
        }
        if (this.responseBodyBytes != null) {
            this.writer.writeBytes(this.responseBodyBytes);
        }
        final InputStream stream;
        if ((stream = resultInputStream()) != null) {
            try (stream; final var out = this.responseBodyOutputStream) {
                stream.transferTo(out);
            }
        }
    }

}
