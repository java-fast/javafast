package io.javafast.http;

/** Transport-independent HTTP cookie. */
public record Cookie(
        String name,
        String value,
        String path,
        int maxAge,
        boolean secure,
        boolean httpOnly,
        String domain,
        SameSite sameSite
) {
    public Cookie(String name, String value) {
        this(name, value, "/", -1, false, false, null, null);
    }

    public Cookie(String name, String value, int maxAge) {
        this(name, value, "/", maxAge, false, false, null, null);
    }

    public Cookie {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Cookie name must not be blank");
        if (value == null) throw new IllegalArgumentException("Cookie value must not be null");
    }
}
