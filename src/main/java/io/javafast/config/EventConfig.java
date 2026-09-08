package io.javafast.config;

import io.javafast.event.HandlerMetaInfo;
import io.javafast.util.function.ThrowingRunnable;

import java.util.function.Consumer;

import static io.javafast.event.ServerLifecycleEvent.*;

public class EventConfig {

    private final JavaFastState config;

    public EventConfig(JavaFastState config) {
        this.config = config;
    }

    /**
     * Adds a callback to react on the Server Starting event
     */
    public void serverStarting(ThrowingRunnable lifecycleEventListener) {
        config.eventManager.addLifecycleEvent(SERVER_STARTING, lifecycleEventListener);
    }

    /**
     * Adds a callback to react on the Server Started event
     */
    public void serverStarted(ThrowingRunnable lifecycleEventListener) {
        config.eventManager.addLifecycleEvent(SERVER_STARTED, lifecycleEventListener);
    }

    /**
     * Adds a callback to react on the Server Start Failed event
     */
    public void serverStartFailed(ThrowingRunnable lifecycleEventListener) {
        config.eventManager.addLifecycleEvent(SERVER_START_FAILED, lifecycleEventListener);
    }

    /**
     * Adds a callback to react on the Server Stop Failed event
     */
    public void serverStopFailed(ThrowingRunnable lifecycleEventListener) {
        config.eventManager.addLifecycleEvent(SERVER_STOP_FAILED, lifecycleEventListener);
    }

    /**
     * Adds a callback to react on the Server Stopping event
     */
    public void serverStopping(ThrowingRunnable lifecycleEventListener) {
        config.eventManager.addLifecycleEvent(SERVER_STOPPING, lifecycleEventListener);
    }

    /**
     * Adds a callback to react on the Server Stopped event
     */
    public void serverStopped(ThrowingRunnable lifecycleEventListener) {
        config.eventManager.addLifecycleEvent(SERVER_STOPPED, lifecycleEventListener);
    }

    /**
     * Adds a callback to react when a [Handler] is added
     */
    public void handlerAdded(Consumer<HandlerMetaInfo> callback) {
        config.eventManager.getHandlerAddedHandlers().add(callback);
    }
}
