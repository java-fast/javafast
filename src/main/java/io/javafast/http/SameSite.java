package io.javafast.http;

public enum SameSite {
    NONE("SameSite=None"),
    STRICT("SameSite=Strict"),
    LAX("SameSite=Lax");

    private final String value;

    SameSite(String value) { this.value = value; }

    public String value() { return value; }

    @Override public String toString() { return value; }
}
