package io.javafast.internal;

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

        final List<Segment> segments = parseSegments(patternPath, rawPath);
        return new RoutePattern(rawPath, segments, wildcard, trailingWildcard, ignoreTrailingSlash, multipleSlashes, caseInsensitive);
    }

    PathParams match(PatternMatch state, String requestPath) {
        state.reset(requestPath);
        if (this.wildcard) return state.params;
        final int count = split(state.splits, requestPath);

        int routeIndex = 0;
        int splitIndex = 0;

        while (routeIndex < this.segments.size()) {
            final Segment segment = this.segments.get(routeIndex);
            if (segment instanceof WildcardSegment) {
                return state.params;
            }

            if (splitIndex >= count) {
                return null;
            }

            if (segment instanceof ParameterSegment(String name, boolean acceptsSlash) && acceptsSlash) {
                final PathSplits.JoinSplit join = state.splits.join(splitIndex);
                state.params.add(name, join.start, join.length);
                splitIndex = count;
                routeIndex++;
                break;
            }

            final PathSplits.PathSplit split = state.splits.split(splitIndex++); // cannot be null: splitIndex >=0 && splitIndex < count
            if (!segment.match(split, state.params, this.caseInsensitive))
                return null;

            routeIndex++;
        }

        if (this.trailingWildcard) {
            return routeIndex == this.segments.size() ? state.params : null;
        }

        if (splitIndex != count) return null;

        // The route compiler removes empty segments, so /foo and /foo/ are equivalent
        // only when the configuration says trailing slashes may be ignored.
        if (!this.ignoreTrailingSlash && !this.multipleSlashes && !sameTrailingSlash(this.rawPath, requestPath)) {
            return null;
        }

        return state.params;
    }

    private boolean sameTrailingSlash(String route, String request) {
        if ("/".equals(route)) return "/".equals(request);
        return route.endsWith("/") == request.endsWith("/");
    }

    private int split(PathSplits state, String path) {
        int count = 0;
        int start = 0;
        for (int i = 0; i <= path.length(); i++) {
            if (i == path.length() || path.charAt(i) == '/') {
                if (i > start) state.set(count++, start, i - start);
                else if (!this.multipleSlashes && i < path.length() && i == start && start > 0) {
                    state.set(count++, start, 0); // empty path segments are meaningful when multiple slashes are not collapsed
                }
                start = i + 1;
            }
        }
        if (count > 0 && state.split(count - 1).empty()) --count; // is the last input empty string?
        return count;
    }

    private static List<Segment> parseSegments(String path, String rawPath) {
        final List<Segment> result = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        final StringBuilder current = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            final char c = path.charAt(i);
            if (c == '/') {
                if (current.length() > 0) {
                    result.add(parseSegment(current.toString(), rawPath, names));
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) result.add(parseSegment(current.toString(), rawPath, names));
        return List.copyOf(result);
    }

    private static Segment parseSegment(String value, String rawPath, Set<String> names) {
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
                    parts.add(new ParamPart(name, null, open == '<'));
                    i = end + 1;
                } else {
                    int end = i + 1;
                    while (end < value.length() && value.charAt(end) != '{' && value.charAt(end) != '<') end++;
                    parts.add(new TextPart(value.substring(i, end)));
                    i = end;
                }
            }
            for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
                if (parts.get(partIndex) instanceof ParamPart p) {
                    parts.set(partIndex, new ParamPart(p.name(), findNextTextPart(parts, partIndex + 1), p.acceptsSlash()));
                }
            }
            return new CompositeSegment(parts.toArray(new Part[0]));
        }
        return new LiteralSegment(value);
    }

    private static TextPart findNextTextPart(List<Part> parts, int from) {
        for (int i = from; i < parts.size(); i++) {
            if (parts.get(i) instanceof TextPart t) {
                return t;
            }
        }
        return null;
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

    private record ParamPart(String name, TextPart next, boolean acceptsSlash) implements RoutePattern.Part {
    }

    private interface Segment {
        boolean match(PathSplits.PathSplit split, PathParams params, boolean caseInsensitive);
    }

    private record LiteralSegment(String value) implements Segment {

        @Override
        public boolean match(PathSplits.PathSplit split, PathParams params, boolean caseInsensitive) {
            return split.match(this.value, caseInsensitive);
        }
    }

    private record ParameterSegment(String name, boolean acceptsSlash) implements Segment {

        @Override
        public boolean match(PathSplits.PathSplit split, PathParams params, boolean caseInsensitive) {
            params.add(this.name, split.start, split.length);
            return true;
        }
    }

    private record CompositeSegment(Part[] parts) implements Segment {

        private CompositeSegment(Part[] parts) {
            this.parts = parts;
        }

        @Override
        public boolean match(PathSplits.PathSplit split, PathParams params, boolean ci) {
            int pos = 0;
            for (final Part part : this.parts) {
                if (part instanceof TextPart(String value)) {
                    final int length = value.length();
                    if (pos + length > split.length || !split.regionMatch(value, pos, ci)) {
                        return false;
                    }
                    pos += length;
                } else if (part instanceof ParamPart p) {
                    final int end = nextTextStart(p.next(), split, pos, ci);
                    if (end < pos) {
                        return false;
                    }
                    params.add(p.name(), split.start + pos, end - pos);
                    pos = end;
                }
            }
            return pos == split.length;
        }

        private static int nextTextStart(TextPart next, PathSplits.PathSplit input, int pos, boolean ci) {
            if (next == null) {
                return input.length;
            }
            final int index = input.lastIndexOf(next.value(), pos, ci);
            return index < 0 ? input.length : index;
        }

    }

    private enum WildcardSegment implements Segment {
        INSTANCE;

        @Override
        public boolean match(PathSplits.PathSplit split, PathParams params, boolean caseInsensitive) {
            return true;
        }
    }

}
