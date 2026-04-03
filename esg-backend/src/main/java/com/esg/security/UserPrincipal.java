package com.esg.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UserPrincipal implements UserDetails {
    private final String id;
    private final String tenantId;
    private final String email;
    private final String password;
    private final String role;
    private final List<String> permissions;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(String id, String tenantId, String email, String password,
                         String role, List<String> permissions,
                         Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.password = password;
        this.role = role;
        this.permissions = permissions;
        this.authorities = authorities;
    }

    public static UserPrincipal create(String id, String tenantId, String email,
                                        String password, String role, List<String> permissions) {
        List<GrantedAuthority> auths = new ArrayList<>(
                permissions.stream().map(SimpleGrantedAuthority::new).map(a -> (GrantedAuthority) a).toList());
        auths.add(new SimpleGrantedAuthority("ROLE_" + role));
        return new UserPrincipal(id, tenantId, email, password, role, permissions, auths);
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getRole() { return role; }
    public List<String> getPermissions() { return permissions; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return email; }
    public String getEmail() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
