package io.javafast.internal;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Compiled route pattern. Matching is performed directly over path segments;
 * java.util.regex is deliberately not used on the request hot path.
 */
final class RoutePattern {

    private final String rawPath;
    private final List<Segment> segments;
    private final boolean wildcard;
    private final boolean trailingWildcard;
    private final boolean ignoreTrailingSlash;
    private final boolean multipleSlashes;
    private final boolean caseInsensitive;

    private RoutePattern(String rawPath,
                         List<Segment> segments,
                         boolean wildcard,
                         boolean trailingWildcard,
                         boolean ignoreTrailingSlash,
                         boolean multipleSlashes,
                         boolean caseInsensitive) {
        this.rawPath = rawPath;
        this.segments = segments;
        this.wildcard = wildcard;
        this.trailingWildcard = trailingWildcard;
        this.ignoreTrailingSlash = ignoreTrailingSlash;
        this.multipleSlashes = multipleSlashes;
        this.caseInsensitive = caseInsensitive;
    }

    static RoutePattern compile(String rawPath, boolean ignoreTrailingSlash, boolean multipleSlashes, boolean caseInsensitive) {
        if (rawPath == null || rawPath.isEmpty()) {
            throw new IllegalArgumentException("Route path must not be empty");
        }
        if (rawPath.contains("/:")) {
            throw new IllegalArgumentException("Path '" + rawPath + "' invalid - use '{param}' instead of ':param'.");
        }

        final boolean trailingWildcard = rawPath.endsWith(">*") || rawPath.endsWith("}*");
        final String patternPath = trailingWildcard ? rawPath.substring(0, rawPath.length() - 1) : rawPath;
        final boolean wildcard = "*".equals(patternPath);

        final List<Segment> segments = parseSegments(patternPath, rawPath, caseInsensitive);
        return new RoutePattern(rawPath, segments, wildcard, trailingWildcard, ignoreTrailingSlash, multipleSlashes, caseInsensitive);
    }

    PathParams match(String requestPath) {
        if (this.wildcard) return PathParams.EMPTY;
        final List<String> input = split(requestPath);

        int routeIndex = 0;
        int inputIndex = 0;
        PathParams params = null;

        while (routeIndex < this.segments.size()) {
            final Segment segment = this.segments.get(routeIndex);
            if (segment instanceof WildcardSegment) {
                return PathParams.EMPTY;
            }

            if (inputIndex >= input.size()) {
                return null;
            }

            if (segment instanceof ParameterSegment(String name, boolean acceptsSlash) && acceptsSlash) {
                if (params == null) params = new PathParams();
                params.put(name, decode(joinRemaining(input, inputIndex)));
                inputIndex = input.size();
                routeIndex++;
                break;
            }

            final String value = input.get(inputIndex++);
            final String captured = segment.match(value, this.caseInsensitive);
            if (captured == null) return null;
            if (segment instanceof ParameterSegment p) {
                if (params == null) params = new PathParams();
                params.put(p.name, decode(captured));
            } else if (segment instanceof CompositeSegment c) {
                if (params == null) params = new PathParams();
                c.capture(value, params);
            }
            routeIndex++;
        }

        if (this.trailingWildcard) {
            return routeIndex == this.segments.size() ? paramsOrEmpty(params) : null;
        }

        if (inputIndex != input.size()) return null;

        // The route compiler removes empty segments, so /foo and /foo/ are equivalent
        // only when the configuration says trailing slashes may be ignored.
        if (!this.ignoreTrailingSlash && !this.multipleSlashes && !sameTrailingSlash(this.rawPath, requestPath)) {
            return null;
        }

        return paramsOrEmpty(params);
    }

    private boolean sameTrailingSlash(String route, String request) {
        if ("/".equals(route)) return "/".equals(request);
        return route.endsWith("/") == request.endsWith("/");
    }

    private List<String> split(String path) {
        final List<String> result = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= path.length(); i++) {
            if (i == path.length() || path.charAt(i) == '/') {
                if (i > start) result.add(path.substring(start, i));
                else if (!this.multipleSlashes && i < path.length() && i == start && start > 0) {
                    result.add(""); // empty path segments are meaningful when multiple slashes are not collapsed
                }
                start = i + 1;
            }
        }
        if (!result.isEmpty() && result.getLast().isEmpty()) result.removeLast();
        return result;
    }

    private static String joinRemaining(List<String> input, int start) {
        return String.join("/", input.subList(start, input.size()));
    }

    private static PathParams paramsOrEmpty(PathParams params) {
        return params == null ? PathParams.EMPTY : params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static List<Segment> parseSegments(String path, String rawPath, boolean caseInsensitive) {
        final List<Segment> result = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        final StringBuilder current = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            final char c = path.charAt(i);
            if (c == '/') {
                if (current.length() > 0) {
                    result.add(parseSegment(current.toString(), rawPath, names, caseInsensitive));
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) result.add(parseSegment(current.toString(), rawPath, names, caseInsensitive));
        return List.copyOf(result);
    }

    private static Segment parseSegment(String value, String rawPath, Set<String> names, boolean caseInsensitive) {
        if ("*".equals(value)) return WildcardSegment.INSTANCE;
        if (value.startsWith("{") && value.endsWith("}") && value.length() > 2) {
            final String name = value.substring(1, value.length() - 1);
            requireName(name, rawPath, names);
            return new ParameterSegment(name, false);
        }
        if (value.startsWith("<") && value.endsWith(">") && value.length() > 2) {
            final String name = value.substring(1, value.length() - 1);
            requireName(name, rawPath, names);
            return new ParameterSegment(name, true);
        }

        // Support composite forms such as "file-{name}.txt" without regex.
        if (value.indexOf('{') >= 0 || value.indexOf('<') >= 0) {
            final List<Part> parts = new ArrayList<>();
            int i = 0;
            while (i < value.length()) {
                final char open = value.charAt(i);
                if (open == '{' || open == '<') {
                    final char close = open == '{' ? '}' : '>';
                    final int end = value.indexOf(close, i + 1);
                    if (end < 0) throw new IllegalArgumentException("Invalid route '" + rawPath + "'");
                    final String name = value.substring(i + 1, end);
                    requireName(name, rawPath, names);
                    parts.add(new ParamPart(name, open == '<'));
                    i = end + 1;
                } else {
                    int end = i + 1;
                    while (end < value.length() && value.charAt(end) != '{' && value.charAt(end) != '<') end++;
                    parts.add(new TextPart(value.substring(i, end)));
                    i = end;
                }
            }
            return new CompositeSegment(parts);
        }
        return new LiteralSegment(value);
    }

    private static void requireName(String name, String path, Set<String> names) {
        if (name.isEmpty() || !names.add(name)) {
            throw new IllegalArgumentException("Duplicate or empty path parameter in '" + path + "'");
        }
    }

    private sealed interface Part permits TextPart, ParamPart {
    }

    private record TextPart(String value) implements RoutePattern.Part {
    }

    private record ParamPart(String name, boolean acceptsSlash) implements RoutePattern.Part {
    }

    private interface Segment {
        String match(String value, boolean caseInsensitive);
    }

    private record LiteralSegment(String value) implements Segment {
        @Override
        public String match(String input, boolean ci) {
            return ci ? this.value.equalsIgnoreCase(input) ? input : null : this.value.equals(input) ? input : null;
        }
    }

    private record ParameterSegment(String name, boolean acceptsSlash) implements Segment {
        @Override
        public String match(String value, boolean ci) {
            return value;
        }
    }

    private record CompositeSegment(List<Part> parts) implements Segment {

        private CompositeSegment(List<Part> parts) {
            this.parts = List.copyOf(parts);
        }

        @Override
        public String match(String input, boolean ci) {
            int pos = 0;
            for (int i = 0; i < this.parts.size(); i++) {
                final Part part = this.parts.get(i);
                if (part instanceof TextPart(String value)) {
                    if (ci ? !input.regionMatches(true, pos, value, 0, value.length())
                        : !input.startsWith(value, pos)) return null;
                    pos += value.length();
                } else if (part instanceof ParamPart) {
                    final int end = nextTextStart(this.parts, i + 1, input, pos, ci);
                    if (end < pos) return null;
                    pos = end;
                }
            }
            return pos == input.length() ? input : null;
        }

        void capture(String input, Map<String, String> out) {
            int pos = 0;
            for (int i = 0; i < this.parts.size(); i++) {
                final Part part = this.parts.get(i);
                if (part instanceof TextPart(String value)) pos += value.length();
                else if (part instanceof ParamPart p) {
                    final int end = nextTextStart(this.parts, i + 1, input, pos, false);
                    out.put(p.name, decode(input.substring(pos, end)));
                    pos = end;
                }
            }
        }

        private static int nextTextStart(List<Part> parts, int from, String input, int pos, boolean ci) {
            for (int j = from; j < parts.size(); j++) {
                if (parts.get(j) instanceof TextPart(String value)) {
                    final int index = ci ? indexOfIgnoreCase(input, value, pos) : input.indexOf(value, pos);
                    return index < 0 ? input.length() : index;
                }
            }
            return input.length();
        }

        private static int indexOfIgnoreCase(String input, String needle, int from) {
            for (int i = from; i + needle.length() <= input.length(); i++) {
                if (input.regionMatches(true, i, needle, 0, needle.length())) return i;
            }
            return -1;
        }
    }

    private enum WildcardSegment implements Segment {
        INSTANCE;

        @Override
        public String match(String value, boolean ci) {
            return value;
        }
    }
}
