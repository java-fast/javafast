package io.javafast.event;

public enum ServerLifecycleEvent {

    // Event fired when attempting to start the Jetty Webserver
    SERVER_STARTING,

    // Event fired when the Jetty Webserver was started successfully
    SERVER_STARTED,

    // Event fired when an exception occurs while starting the Jetty Webserver
    SERVER_START_FAILED,

    // Event fired when an exception occurs while stopping the Jetty Webserver
    SERVER_STOP_FAILED,

    // Event fired when attempting to stop the Jetty Webserver
    SERVER_STOPPING,

    // Event fired after the Jetty Webserver was stopped successfully
    SERVER_STOPPED

}
