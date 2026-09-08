package io.javafast.json;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.stream.Stream;

public interface JsonMapper {
    public String toJsonString(Object obj, Type type);
    public InputStream toJsonStream(Object obj, Type type);
    public void writeToOutputStream(Stream<?> stream, OutputStream outputStream);
    public <T extends Object> T fromJsonString(String json, Type targetType);
    public <T extends Object> T fromJsonStream(InputStream json, Type targetType);
}
