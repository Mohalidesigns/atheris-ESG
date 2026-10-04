package com.esg.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method that is intentionally reachable without a permission check (login, token
 * refresh, MFA verification, and later the public whistleblower and certificate-verification endpoints).
 * Every other endpoint must carry {@code @PreAuthorize}; ControllerSecurityArchTest enforces this.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PublicEndpoint {
    /** Why this endpoint is public. */
    String value();
}
