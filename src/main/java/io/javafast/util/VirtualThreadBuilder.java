package io.javafast.util;

public final class VirtualThreadBuilder {
        public interface Builder {
            public Builder name(String name);
            public Thread unstarted(Runnable runnable);
        }
}
