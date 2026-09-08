package io.javafast.util;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.Map;

public final class Util {
    public static void normalizeContextPath(String contextPath) {}
    public static void prefixContextPath(String contextPath, String path) {}
    public static void classExists(String className) {}
    public static void printHelpfulMessageIfLoggerIsMissing() {}
    public static String checksumAndReset(ByteArrayInputStream inputStream) {
        return "";
    }
    public static URL resourceUrl(String path) {
        return null;
    }
    public static URL fileUrl(String path) {
        return null;
    }
    public static void port(Exception e) {}
    public static <T> T findByClass(Map<Class<? extends Exception>, T> map, Class<? extends Exception> exceptionClass) {
        return null;
    }
}
