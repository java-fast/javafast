package io.javafast.internal;

/**
 * Thread-confiden state for {@link RoutePattern}
 */
class PatternMatch {

    private static final int MAX_PATH_SPLITS = Integer.getInteger("javafast.max.path.splits", 64); // max allowed path splits
    private static final int MAX_PATH_PARAMS = Integer.getInteger("javafast.max.path.params", 16); // max allowed path params

    final PathSplits splits = new PathSplits(MAX_PATH_SPLITS);
    final PathParams params = new PathParams(MAX_PATH_PARAMS);

    void reset(String requestPath) {
        this.splits.reset(requestPath);
        this.params.reset(requestPath);
    }

}
