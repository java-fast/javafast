package io.javafast.transport;

public class MalformedRequestException extends RuntimeException {

    public MalformedRequestException(String message) {
        super(message);
    }

    public static MalformedRequestException notSupported() {
        return new MalformedRequestException("HTTP Version is not supported");
    }

    public static MalformedRequestException malformed() {
        return new MalformedRequestException("Malformed request");
    }

}
