package io.javafast.router;

import io.javafast.http.Context;

/**
 * Optional custom access controll runs on the BEFORE_MATCHED phase
 */
public interface AccessController {

    boolean isAllowed(Context context);
}
