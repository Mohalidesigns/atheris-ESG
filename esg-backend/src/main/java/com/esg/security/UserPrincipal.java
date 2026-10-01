package com.esg.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated user for one request. Authorities are the user's current permissions
 * ({@code resource:action}) plus {@code ROLE_<name>} for each active role, loaded from the database by
 * {@link AccessService} — not taken from the JWT, which may be up to 15 minutes old.
 */
public class UserPrincipal {

    private final String id;
    private final String tenantId;
    private final UserAccess access;
    private final List<GrantedAuthority> authorities;

    public UserPrincipal(UserAccess access) {
        this.id = access.userId().toString();
        this.tenantId = access.tenantId().toString();
        this.access = access;
        List<GrantedAuthority> auths = new ArrayList<>();
        access.permissions().forEach(p -> auths.add(new SimpleGrantedAuthority(p)));
        access.roles().forEach(r -> auths.add(new SimpleGrantedAuthority("ROLE_" + r)));
        this.authorities = List.copyOf(auths);
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UserAccess getAccess() { return access; }
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
}
