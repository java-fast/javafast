package io.javafast.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {

    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    public NamedThreadFactory(String namePrefix) {
        this.namePrefix = namePrefix + "-thread-";
    }

    public Thread newThread(Runnable runnable) {
        return Thread.ofPlatform()
            .name(this.namePrefix + threadNumber.getAndIncrement())
            .unstarted(runnable);
    }

}
