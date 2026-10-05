package io.javafast.internal;

class PathSplits {

    private final PathSplit[] splits;
    private final JoinSplit joinSplit = new JoinSplit();
    private ByteString requestPath;

    PathSplits(int maxSize) {
        this.splits = new PathSplit[maxSize];
        for (int i = 0; i < this.splits.length; i++) {
            this.splits[i] = new PathSplit();
        }
    }

    void reset(ByteString requestPath) {
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

        private void set(int start, int length) {
            this.start = start;
            this.length = length;
        }

        boolean empty() {
            return this.start > 0 && this.length == 0;
        }

        boolean match(String test, boolean caseInsensitive) {
            return test.length() == this.length
                && requestPath.regionMatches(
                caseInsensitive,
                this.start,
                test,
                0,
                this.length
            );
        }

        boolean regionMatch(String value, int offset, boolean caseInsensitive) {
            return requestPath.regionMatches(
                caseInsensitive,
                this.start + offset,
                value,
                0,
                value.length()
            );
        }

        /**
         * Unlike Java's String.lastIndexOf, this method returns the index of the last occurrence of the given test string within the current path segment.
         * @return index relative to this PathSplit
         */
        int lastIndexOf(String test, int offset, boolean caseInsensitive) {
            final int needleLength = test.length();
            final int max = Math.min(this.start + this.length, requestPath.length() - needleLength + 1);
            int last = -1;
            for (int i = this.start + offset; i < max; i++) {
                if (requestPath.regionMatches(caseInsensitive, i, test, 0, needleLength)) {
                    last = i;
                }
            }
            return last - this.start;
        }

    }

    class JoinSplit extends PathSplit {

        private JoinSplit init(int start, int length) {
            super.set(start, length);
            return this;
        }
    }

}
