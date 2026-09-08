package io.javafast.security;

import io.javafast.http.Context;

public interface AccessController {

    boolean isAllowed(Context context);
}
