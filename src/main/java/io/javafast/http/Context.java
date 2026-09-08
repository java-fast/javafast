package io.javafast.http;

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
import java.util.stream.Stream;

/**
 * Request/response context exposed to application handlers.
 */
public interface Context {

    // Request metadata
    int contentLength();

    String contentType();

    HttpMethod method();

    String path();

    int port();

    String protocol();

    String contextPath();

    String userAgent();

    String characterEncoding();

    String url();

    String fullUrl();

    String scheme();

    String host();

    String ip();

    String queryString();

    // Application data / extensions
    <T> T appData(String key);

    JsonMapper jsonMapper();

    <T> T with(Class<? extends ContextPlugin<?, T>> pluginClass);

    // Request body
    String body();

    byte[] bodyAsBytes();

    <T> T bodyAsClass(Type type);

    <T> T bodyAsClass(Class<T> type);

    <T> T bodyStreamAsClass(Type type);

    InputStream bodyInputStream();

    <T> BodyValidator<T> bodyValidator(Class<T> type);

    // Parameters
    String formParam(String key);

    <T> Validator<T> formParamAsClass(String key, Class<T> type);

    List<String> formParams(String key);

    <T> Validator<List<T>> formParamsAsClass(String key, Class<T> type);

    Map<String, List<String>> formParamMap();

    String pathParam(String key);

    <T> Validator<T> pathParamAsClass(String key, Class<T> type);

    Map<String, String> pathParamMap();

    String queryParam(String key);

    <T> Validator<T> queryParamAsClass(String key, Class<T> type);

    List<String> queryParams(String key);

    <T> Validator<List<T>> queryParamsAsClass(String key, Class<T> type);

    Map<String, List<String>> queryParamMap();

    // Request attributes
    void attribute(String key, Object value);

    <T> T attribute(String key);

    <T> T attributeOrCompute(String key, java.util.function.Function<Context, T> callback);

    Map<String, Object> attributeMap();

    // Headers / cookies
    String header(String name);

    <T> Validator<T> headerAsClass(String name, Class<T> type);

    Map<String, String> headerMap();

    String cookie(String name);

    Map<String, String> cookieMap();

    String basicAuthCredentials();

    // Multipart
    boolean isMultipart();

    boolean isMultipartFormData();

    boolean isFormUrlencoded();

    boolean isJson();

    UploadedFile uploadedFile(String name);

    List<UploadedFile> uploadedFiles(String name);

    List<UploadedFile> uploadedFiles();

    Map<String, List<UploadedFile>> uploadedFileMap();

    // Response
    Charset responseCharset();

    OutputStream outputStream();

    Context result(String value);

    Context result(byte[] bytes);

    Context result(InputStream stream);

    String result();

    InputStream resultInputStream();

    Context contentType(String contentType);

    Context header(String name, String value);

    Context addHeader(String name, String value);

    Context removeHeader(String name);

    Context redirect(String location, HttpStatus status);

    default Context redirect(String location) {
        return redirect(location, HttpStatus.FOUND);
    }

    Context status(HttpStatus status);

    Context status(int status);

    HttpStatus status();

    int statusCode();

    Context cookie(String name, String value);

    Context cookie(String name, String value, int maxAge);

    Context cookie(Cookie cookie);

    Context removeCookie(String name, String path);

    default Context removeCookie(String name) {
        return removeCookie(name, "/");
    }

    Context json(Object object, Type type);

    default Context json(Object object) {
        return json(object, object.getClass());
    }

    Context jsonStream(Object object, Type type);

    default Context jsonStream(Object object) {
        return jsonStream(object, object.getClass());
    }

    void writeJsonStream(Stream<?> stream);

    default Context html(String html) {
        return contentType(ContentType.HTML).result(html);
    }

    /**
     * Stop executing remaining handlers for this request.
     */
    Context skipRemainingHandlers();
}
