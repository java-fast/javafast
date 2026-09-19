package io.javafast.internal;

public class PathSplits {

    private final PathSplit[] splits;
    private final JoinSplit joinSplit = new JoinSplit();
    private String requestPath;

    public PathSplits(int maxSize) {
        this.splits = new PathSplit[maxSize];
        for (int i = 0; i < this.splits.length; i++) {
            this.splits[i] = new PathSplit();
        }
    }

    void reset(String requestPath) {
        this.requestPath = requestPath;
    }

    void set(int index, int start, int length) {
        this.splits[index].set(start, length);
    }

    PathSplit split(int index) {
        return this.splits[index];
    }

    JoinSplit join(int start) {
        final int begin = this.splits[start].start;
        return this.joinSplit.init(begin, this.requestPath.length() - begin);
    }

    class PathSplit {

        int start;
        int length;

        private String value; // lazy

        private void set(int start, int length) {
            this.start = start;
            this.length = length;
        }

        String get() {
            if (this.value == null) {
                this.value = requestPath.substring(this.start, this.start + this.length);
            }
            return this.value;
        }

        boolean empty() {
            return this.start > 0 && this.length == 0;
        }

        boolean match(String test, boolean caseInsensitive) {
            return test.length() == this.length
                && test.regionMatches(
                caseInsensitive,
                0,
                requestPath,
                this.start,
                this.length
            );
        }

    }

    class JoinSplit extends PathSplit {

        private JoinSplit init(int start, int length) {
            super.set(start, length);
            return this;
        }
    }

}
