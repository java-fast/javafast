package io.javafast.config;

import io.javafast.router.HandlerWrapper;

public class RouterConfig {
    public RouterConfig() {}
    public String contextPath;
    public boolean ignoreTrailingSlashes;
    public boolean treatMultipleSlashesAsSingleSlash;
    public boolean caseInsensitiveRoutes;
    public void handlerWrapper(HandlerWrapper handlerWrapper) {}
}
