package com.esg.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests carrying a valid <em>access</em> token. The token only identifies the user and tenant;
 * roles, permissions and org scopes are reloaded from the database for every request, so a deactivated user or
 * an expired role assignment loses access immediately (TRD §11.2.1).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final AccessService accessService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, AccessService accessService) {
        this.tokenProvider = tokenProvider;
        this.accessService = accessService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Recorded for every request (including login) so database-level audit rows carry the client (V014).
            TenantContext.setClient(request.getRemoteAddr(), request.getHeader("User-Agent"));
            Optional<Claims> claims = Optional.ofNullable(extractToken(request)).flatMap(tokenProvider::parseAccessToken);
            if (claims.isPresent()) {
                authenticate(claims.get(), request);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        String tenantId = claims.get("tenant_id", String.class);
        String userId = claims.getSubject();
        if (tenantId == null || userId == null) {
            return;
        }
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(userId);

        UserAccess access = accessService.load(UUID.fromString(userId));
        if (!access.active()) {
            TenantContext.clear();
            return;
        }
        UserPrincipal principal = new UserPrincipal(access);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
