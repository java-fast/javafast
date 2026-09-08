package io.javafast.event;

import io.javafast.util.function.ThrowingRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class EventManager {

    private final Map<ServerLifecycleEvent, ThrowingRunnable> lifecycleHandlers = new HashMap<>();
    private final Set<Consumer<HandlerMetaInfo>> handlerAddedHandlers = new HashSet<>();

    public Map<ServerLifecycleEvent, ThrowingRunnable> getLifecycleHandlers() {
        return lifecycleHandlers;
    }

    public Set<Consumer<HandlerMetaInfo>> getHandlerAddedHandlers() {
        return handlerAddedHandlers;
    }

    public void fireEvent(ServerLifecycleEvent serverLifecycleEvent) {
    }

    public void fireHandlerAddedEvent(HandlerMetaInfo metaInfo) {
    }

    public void addLifecycleEvent(ServerLifecycleEvent event, ThrowingRunnable handler) {
        lifecycleHandlers.put(event, handler);
    }

}
