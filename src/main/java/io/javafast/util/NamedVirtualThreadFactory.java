package io.javafast.util;

public class NamedVirtualThreadFactory extends NamedThreadFactory {

    public NamedVirtualThreadFactory(String namePrefix) {
        super(namePrefix);
    }

    public interface Builder {
        public Builder name(String name);

        public Thread unstarted(Runnable runnable);
    }
}
