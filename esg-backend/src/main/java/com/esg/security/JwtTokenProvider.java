package com.esg.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Signs and verifies JWTs (TRD §5.4). Two token types, distinguished by the {@code typ} claim:
 * <ul>
 *   <li>{@code access}: 15-minute bearer token for API calls.</li>
 *   <li>{@code mfa}: 5-minute token that only allows completing MFA setup/verification after a correct password.</li>
 * </ul>
 * Refresh tokens are opaque and stored hashed ({@link RefreshTokenService}), so they can never be used as bearer
 * tokens (AS-IS S2).
 */
@Component
public class JwtTokenProvider {

    static final int MIN_SECRET_BYTES = 64;
    static final String TYPE_ACCESS = "access";
    static final String TYPE_MFA = "mfa";
    private static final long MFA_TOKEN_MS = 5 * 60 * 1000;

    private final SecretKey key;
    private final long jwtExpirationMs;

    public JwtTokenProvider(
            @Value("${esg.security.jwt-secret}") String jwtSecret,
            @Value("${esg.security.jwt-expiration-ms}") long jwtExpirationMs) {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes (HS512)");
        }
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationMs = jwtExpirationMs;
    }

    /**
     * JWT payload per TRD §5.4.1: sub, tenant_id, roles[], permissions[] (informational; the server re-checks), and
     * {@code mfa}: true when the session was started with a verified TOTP code. Users holding an MFA-required role
     * are refused without it ({@link JwtAuthenticationFilter}).
     */
    public String generateAccessToken(String userId, String tenantId, Collection<String> roles,
                                      Collection<String> permissions, boolean mfa) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claims(Map.of(
                        "typ", TYPE_ACCESS,
                        "tenant_id", tenantId,
                        "roles", List.copyOf(roles),
                        "permissions", List.copyOf(permissions),
                        "mfa", mfa))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + jwtExpirationMs))
                .signWith(key)
                .compact();
    }

    public String generateMfaToken(String userId, String tenantId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claims(Map.of("typ", TYPE_MFA, "tenant_id", tenantId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + MFA_TOKEN_MS))
                .signWith(key)
                .compact();
    }

    /** Claims of a valid access token, or empty for anything else (bad signature, expired, wrong type). */
    public Optional<Claims> parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    public Optional<Claims> parseMfaToken(String token) {
        return parse(token, TYPE_MFA);
    }

    private Optional<Claims> parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return expectedType.equals(claims.get("typ", String.class)) ? Optional.of(claims) : Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getJwtExpirationMs() {
        return jwtExpirationMs;
    }
}
