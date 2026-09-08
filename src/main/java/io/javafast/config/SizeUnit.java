package io.javafast.config;

public enum SizeUnit {

    BYTES(1), KB(1024), MB(1024 * 1024), GB(1024 * 1024 * 1024);

    public final int multiplier;

    public int getMultiplier() {
        return multiplier;
    }

    SizeUnit(int multiplier) {
        this.multiplier = multiplier;
    }
}
