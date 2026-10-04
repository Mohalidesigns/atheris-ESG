package com.esg.security;

import com.esg.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Opaque, rotating refresh tokens stored hashed in auth.user_sessions (TRD §5.4.1–5.4.2).
 *
 * <p>Format: {@code <tenant-uuid>.<256 random bits, base64url>}. The tenant prefix isn't secret; it lets the
 * session be looked up under RLS. Only the SHA-256 hash is stored. Each refresh revokes the old session and
 * creates a new one. Presenting an already-revoked token is treated as theft and revokes every session of that
 * user — except within {@link #REUSE_GRACE} of the rotation, which is what concurrent refreshes from two
 * browser tabs (or React StrictMode's double effects) look like; those are simply rejected.
 */
@Service
public class RefreshTokenService {

    static final java.time.Duration REUSE_GRACE = java.time.Duration.ofSeconds(30);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final long refreshExpirationMs;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(JdbcTemplate jdbc, TransactionTemplate tx,
                               @Value("${esg.security.refresh-expiration-ms}") long refreshExpirationMs) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /** @param mfaVerified the session was started with a verified TOTP code; carried over on every rotation */
    public record Rotation(UUID userId, String refreshToken, boolean mfaVerified) {}

    /** Creates a session for an authenticated user (tenant context must already be set). */
    public String issue(UUID userId, UUID tenantId, String userAgent, String ip, boolean mfaVerified) {
        String token = tenantId + "." + randomSecret();
        jdbc.update("insert into auth.user_sessions (user_id, refresh_token_hash, device_info, ip_address, expires_at, mfa_verified) "
                        + "values (?, ?, jsonb_build_object('userAgent', ?::text), ?::inet, ?, ?)",
                userId, hash(token), truncate(userAgent), ip, Timestamp.from(Instant.now().plusMillis(refreshExpirationMs)),
                mfaVerified);
        return token;
    }

    /** Validates and rotates a refresh token. Sets the tenant context from the token. */
    public Rotation rotate(String token, String userAgent, String ip) {
        UUID tenantId = tenantOf(token);
        TenantContext.setTenantId(tenantId.toString());
        Rotation rotation = tx.execute(status -> {
            List<Object[]> rows = jdbc.query(
                    "select id, user_id, expires_at, revoked_at, mfa_verified from auth.user_sessions "
                            + "where refresh_token_hash = ? for update",
                    (rs, i) -> new Object[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getTimestamp(3), rs.getTimestamp(4), rs.getBoolean(5)},
                    hash(token));
            if (rows.isEmpty()) {
                return null;
            }
            UUID sessionId = (UUID) rows.get(0)[0];
            UUID userId = (UUID) rows.get(0)[1];
            Timestamp revokedAt = (Timestamp) rows.get(0)[3];
            if (revokedAt != null) {
                if (revokedAt.toInstant().isBefore(Instant.now().minus(REUSE_GRACE))) {
                    // Replay of an old rotated/revoked token: assume theft, end every session of this user.
                    revokeAllSessions(userId);
                }
                return new Rotation(userId, null, false);
            }
            if (((Timestamp) rows.get(0)[2]).toInstant().isBefore(Instant.now())) {
                return null;
            }
            jdbc.update("update auth.user_sessions set revoked_at = now() where id = ?", sessionId);
            TenantContext.setUserId(userId.toString());
            boolean mfa = (Boolean) rows.get(0)[4];
            return new Rotation(userId, issue(userId, tenantId, userAgent, ip, mfa), mfa);
        });
        if (rotation == null || rotation.refreshToken() == null) {
            throw ApiException.unauthorized("Invalid or expired refresh token");
        }
        return rotation;
    }

    /** Revokes one session (logout). Unknown or malformed tokens are ignored. */
    public void revoke(String token) {
        UUID tenantId;
        try {
            tenantId = tenantOf(token);
        } catch (ApiException e) {
            return;
        }
        TenantContext.setTenantId(tenantId.toString());
        jdbc.update("update auth.user_sessions set revoked_at = now() where refresh_token_hash = ? and revoked_at is null",
                hash(token));
    }

    public void revokeAllSessions(UUID userId) {
        jdbc.update("update auth.user_sessions set revoked_at = now() where user_id = ? and revoked_at is null", userId);
    }

    private static UUID tenantOf(String token) {
        if (token == null || token.indexOf('.') != 36) {
            throw ApiException.unauthorized("Invalid or expired refresh token");
        }
        try {
            return UUID.fromString(token.substring(0, 36));
        } catch (IllegalArgumentException e) {
            throw ApiException.unauthorized("Invalid or expired refresh token");
        }
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String value) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), 300));
    }
}
