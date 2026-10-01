package com.esg.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class JwtTokenProvider {

    static final int MIN_SECRET_BYTES = 64;

    private final SecretKey key;
    private final long jwtExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
            @Value("${esg.security.jwt-secret}") String jwtSecret,
            @Value("${esg.security.jwt-expiration-ms}") long jwtExpirationMs,
            @Value("${esg.security.refresh-expiration-ms}") long refreshExpirationMs) {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes (HS512)");
        }
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationMs = jwtExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(String userId, String tenantId, String role, List<String> permissions) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claims(Map.of(
                        "tenant_id", tenantId,
                        "role", role,
                        "permissions", permissions
                ))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + jwtExpirationMs))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(String userId, String tenantId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                // The tenant is needed to load the user under RLS during refresh (WP-11).
                // Distinguishing refresh from access tokens is WP-12 (AS-IS S2).
                .claim("tenant_id", tenantId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpirationMs))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        return parseToken(token).getSubject();
    }

    public String getTenantIdFromToken(String token) {
        return parseToken(token).get("tenant_id", String.class);
    }

    public long getJwtExpirationMs() {
        return jwtExpirationMs;
    }
}
