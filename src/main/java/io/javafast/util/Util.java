package io.javafast.util;

public final class Util {

    public static String normalizeContextPath(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "";
        }

        final String withLeadingSlash = contextPath.startsWith("/") ? contextPath : "/" + contextPath;
        int end = withLeadingSlash.length();
        while (end > 1 && withLeadingSlash.charAt(end - 1) == '/') {
            end--;
        }
        return withLeadingSlash.substring(0, end);
    }

    public static String prefixContextPath(String contextPath, String path) {
        final String normalizedContextPath = normalizeContextPath(contextPath);
        if (normalizedContextPath.isEmpty()) {
            return path;
        }
        if ("/".equals(path)) {
            return normalizedContextPath + "/";
        }
        return normalizedContextPath + path;
    }

}
