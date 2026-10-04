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
 *
 * <p>Users holding an MFA-required role must present a token from an MFA-verified session ({@code mfa=true});
 * anything else is refused with 401 ESG-401-MFA-REQUIRED on every endpoint (S1), including a role granted after
 * the session started.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Public whistleblower endpoints (WP-34): no session, no client details. */
    public static final String PUBLIC_WHISTLEBLOWER = "/v1/public/whistleblower/";

    private final JwtTokenProvider tokenProvider;
    private final AccessService accessService;
    private final MfaPolicy mfaPolicy;
    private final TestDataPolicy testData;
    private final com.esg.integrations.ApiKeyService apiKeys;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, AccessService accessService, MfaPolicy mfaPolicy,
                                   TestDataPolicy testData, com.esg.integrations.ApiKeyService apiKeys) {
        this.apiKeys = apiKeys;
        this.tokenProvider = tokenProvider;
        this.accessService = accessService;
        this.mfaPolicy = mfaPolicy;
        this.testData = testData;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Recorded for every request (including login) so database-level audit rows carry the client (V014).
            // The anonymous whistleblower channel records no client details at all (TRD §6.5.5: IP not logged).
            String path = request.getRequestURI().substring(request.getContextPath() == null ? 0 : request.getContextPath().length());
            if (path.startsWith(PUBLIC_WHISTLEBLOWER)) {
                TenantContext.setClient(null, null);
            } else {
                TenantContext.setClient(request.getRemoteAddr(), request.getHeader("User-Agent"));
            }
            TenantContext.setTestRun(testData.runOf(request));
            // WP-46 (D44): machine access with an API key (X-API-Key, or Authorization: ApiKey …) instead of a session.
            String apiKey = extractApiKey(request);
            if (apiKey != null && extractToken(request) == null) {
                if (!authenticateApiKey(apiKey, request)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/problem+json");
                    response.getWriter().write(com.esg.exception.Problems.json(org.springframework.http.HttpStatus.UNAUTHORIZED,
                            "ESG-401-API-KEY", "The API key is unknown, revoked or expired", request.getRequestURI()));
                    return;
                }
                filterChain.doFilter(request, response);
                return;
            }
            Optional<Claims> claims = Optional.ofNullable(extractToken(request)).flatMap(tokenProvider::parseAccessToken);
            if (claims.isPresent() && !authenticate(claims.get(), request)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/problem+json");
                response.getWriter().write(com.esg.exception.Problems.json(org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "ESG-401-MFA-REQUIRED", "Your role requires two-factor authentication. Sign in again to set it up.",
                        request.getRequestURI()));
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /** False only when the user needs an MFA-verified session and this token isn't from one. */
    private boolean authenticate(Claims claims, HttpServletRequest request) {
        String tenantId = claims.get("tenant_id", String.class);
        String userId = claims.getSubject();
        if (tenantId == null || userId == null) {
            return true;
        }
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(userId);

        UserAccess access = accessService.load(UUID.fromString(userId));
        if (!access.active()) {
            TenantContext.clear();
            return true;
        }
        if (mfaPolicy.required(access) && !Boolean.TRUE.equals(claims.get("mfa", Boolean.class))) {
            TenantContext.clear();
            return false;
        }
        UserPrincipal principal = new UserPrincipal(access);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return true;
    }

    private boolean authenticateApiKey(String raw, HttpServletRequest request) {
        Optional<com.esg.integrations.ApiKeyService.Authenticated> key = apiKeys.authenticate(raw);
        if (key.isEmpty()) {
            TenantContext.setTenantId(null);
            TenantContext.setUserId(null);
            return false;
        }
        // Audit rows show the key's creator as the user and the key as the client.
        TenantContext.setClient(request.getRemoteAddr(), "API key " + key.get().name() + " (" + key.get().prefix() + ")");
        UserPrincipal principal = new UserPrincipal(key.get().access(), key.get().keyId());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return true;
    }

    private static String extractApiKey(HttpServletRequest request) {
        String header = request.getHeader("X-API-Key");
        if (StringUtils.hasText(header)) {
            return header.trim();
        }
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("ApiKey ")) {
            return authorization.substring(7).trim();
        }
        return null;
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
