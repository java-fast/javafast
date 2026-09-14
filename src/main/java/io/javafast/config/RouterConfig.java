package io.javafast.config;

import io.javafast.router.HandlerWrapper;

public class RouterConfig {

    // TODO implement
    public String contextPath;

    public boolean ignoreTrailingSlashes;
    public boolean treatMultipleSlashesAsSingleSlash;
    public boolean caseInsensitiveRoutes;
    public HandlerWrapper handlerWrapper;

    public void handlerWrapper(HandlerWrapper handlerWrapper) {
        this.handlerWrapper = handlerWrapper;
    }
}
