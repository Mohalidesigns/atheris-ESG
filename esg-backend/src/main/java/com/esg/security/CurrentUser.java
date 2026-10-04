package com.esg.security;

import com.esg.exception.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the authenticated {@link UserPrincipal} and its {@link UserAccess}. */
public final class CurrentUser {

    private CurrentUser() {}

    public static UserPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw ApiException.unauthorized("Authentication required");
        }
        return principal;
    }

    public static UserAccess access() {
        return principal().getAccess();
    }

    /** The signed-in user's id, or null outside a request (scheduled jobs, report rendering). */
    public static java.util.UUID userIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal principal ? principal.getAccess().userId() : null;
    }

    public static boolean hasPermission(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permission));
    }
}
