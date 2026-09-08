package io.javafast.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Function;

/**
 * Transport-independent metadata and content for an uploaded multipart file.
 */
public final class UploadedFile {
    private final InputStream content;
    private final String contentType;
    private final String filename;
    private final long size;

    public UploadedFile(InputStream content, String contentType, String filename, long size) {
        this.content = Objects.requireNonNull(content, "content");
        this.contentType = contentType;
        this.filename = Objects.requireNonNull(filename, "filename");
        this.size = size;
    }

    public InputStream content() {
        return content;
    }

    public String contentType() {
        return contentType;
    }

    public String filename() {
        return filename;
    }

    public long size() {
        return size;
    }

    public String extension() {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : filename;
    }

    public <T> T contentAndClose(Function<InputStream, T> callback) throws IOException {
        try (InputStream input = content) {
            return callback.apply(input);
        }
    }
}
