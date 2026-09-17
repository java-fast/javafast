package io.javafast.http;

/**
 * Execution phases of the normal HTTP handler pipeline.
 */
public enum HandlerPhase {
    BEFORE,
    BEFORE_MATCHED,
    AFTER_MATCHED,
    AFTER;

    public int key() {
        return this.ordinal() + HttpMethod.MAX_COUNT;
    }

}
