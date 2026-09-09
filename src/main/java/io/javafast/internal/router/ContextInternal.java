package io.javafast.internal.router;

import io.javafast.http.*;
import io.javafast.json.JsonMapper;
import io.javafast.plugin.ContextPlugin;
import io.javafast.validation.BodyValidator;
import io.javafast.validation.Validator;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

class ContextInternal implements Context {

    String contextPath;
    Map<String, String> pathParamMap;
    Map<String, List<String>> headerMap;

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
        return null;
    }

    @Override
    public String path() {
        return "";
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
        return "";
    }

    @Override
    public <T> T appData(String key) {
        return null;
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
        return "";
    }

    @Override
    public byte[] bodyAsBytes() {
        return new byte[0];
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
    public String pathParam(String key) {
        return "";
    }

    @Override
    public <T> Validator<T> pathParamAsClass(String key, Class<T> type) {
        return null;
    }

    @Override
    public Map<String, String> pathParamMap() {
        return Map.of();
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
        return "";
    }

    @Override
    public <T> Validator<T> headerAsClass(String name, Class<T> type) {
        return null;
    }

    @Override
    public Map<String, String> headerMap() {
        return Map.of();
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
        return null;
    }

    @Override
    public Context result(String value) {
        return null;
    }

    @Override
    public Context result(byte[] bytes) {
        return null;
    }

    @Override
    public Context result(InputStream stream) {
        return null;
    }

    @Override
    public String result() {
        return "";
    }

    @Override
    public InputStream resultInputStream() {
        return null;
    }

    @Override
    public Context contentType(String contentType) {
        return null;
    }

    @Override
    public Context header(String name, String value) {
        return null;
    }

    @Override
    public Context addHeader(String name, String value) {
        return null;
    }

    @Override
    public Context removeHeader(String name) {
        return null;
    }

    @Override
    public Context redirect(String location, HttpStatus status) {
        return null;
    }

    @Override
    public Context status(HttpStatus status) {
        return null;
    }

    @Override
    public Context status(int status) {
        return null;
    }

    @Override
    public HttpStatus status() {
        return null;
    }

    @Override
    public int statusCode() {
        return 0;
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
        return null;
    }

    void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    void setPathParamMap(Map<String, String> pathParamMap) {
        this.pathParamMap = pathParamMap;
    }

    void setHeaderMap(Map<String, List<String>> headerMap) {
        this.headerMap = headerMap;
    }

}
