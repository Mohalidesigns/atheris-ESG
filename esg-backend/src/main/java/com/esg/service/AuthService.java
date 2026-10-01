package com.esg.service;

import com.esg.exception.ApiException;
import com.esg.model.dto.AuthResponse;
import com.esg.model.dto.LoginRequest;
import com.esg.model.dto.MfaRequests;
import com.esg.model.entity.User;
import com.esg.repository.UserRepository;
import com.esg.security.AccessService;
import com.esg.security.CurrentUser;
import com.esg.security.JwtTokenProvider;
import com.esg.security.RefreshTokenService;
import com.esg.security.SecretEncryptor;
import com.esg.security.TenantContext;
import com.esg.security.TotpService;
import com.esg.security.UserAccess;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Authentication (TRD §5.4): password login with lockout, TOTP MFA (enforced for roles flagged
 * {@code mfa_required}), opaque rotating refresh tokens, logout and the current-user profile.
 *
 * <p>The tenant isn't known before login and auth.users is protected by RLS, so the email is first resolved to
 * (user, tenant) through auth.find_login_identity(). Every failure returns the same message so accounts can't
 * be enumerated (AS-IS S13).
 */
@Service
public class AuthService {

    static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_SECONDS = 1800;
    private static final String MFA_ISSUER = "Atheris ESG";
    /** BCrypt hash of random bytes nobody knows; used to equalise timing for unknown emails. */
    private static final String DUMMY_HASH = "$2a$10$eCnE6e4XI89jCW7bPIJ7z.15LyGPJokVp7ktEEooIvxjdhOZUOp/y";

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokens;
    private final AccessService accessService;
    private final TotpService totp;
    private final SecretEncryptor encryptor;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final boolean enforceMfa;

    public AuthService(UserRepository userRepository, JwtTokenProvider tokenProvider, RefreshTokenService refreshTokens,
                       AccessService accessService, TotpService totp, SecretEncryptor encryptor,
                       PasswordEncoder passwordEncoder, JdbcTemplate jdbc, TransactionTemplate tx,
                       @Value("${esg.security.mfa.enforce:true}") boolean enforceMfa) {
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.refreshTokens = refreshTokens;
        this.accessService = accessService;
        this.totp = totp;
        this.encryptor = encryptor;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.tx = tx;
        this.enforceMfa = enforceMfa;
    }

    /** Response body plus the refresh token the controller puts in an HttpOnly cookie (null for MFA challenges). */
    public record AuthResult(AuthResponse body, String refreshToken) {}

    /** Client details stored with the session. */
    public record ClientInfo(String userAgent, String ip) {}

    public AuthResult login(LoginRequest request, ClientInfo client) {
        LoginIdentity identity = findLoginIdentity(request.getEmail());
        if (identity == null) {
            // Same work as a real check, so response time doesn't reveal whether the account exists.
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        setContext(identity.tenantId(), identity.userId());

        Object outcome = tx.execute(status -> {
            User user = userRepository.findById(identity.userId()).orElse(null);
            if (user == null || !user.getIsActive() || isLocked(user)) {
                return INVALID_CREDENTIALS;
            }
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                registerFailure(user);
                return INVALID_CREDENTIALS;
            }
            UserAccess access = accessService.load(user.getId());
            if (user.getMfaEnabled()) {
                return new AuthResult(AuthResponse.mfaChallenge(mfaToken(user), false), null);
            }
            if (enforceMfa && requiresMfa(access.roles())) {
                return new AuthResult(AuthResponse.mfaChallenge(mfaToken(user), true), null);
            }
            return completeLogin(user, access, client);
        });
        if (outcome instanceof String error) {
            throw ApiException.unauthorized(error);
        }
        return (AuthResult) outcome;
    }

    /** Starts TOTP enrolment for a user who has passed the password step. */
    public MfaRequests.SetupResponse setupMfa(MfaRequests.Setup request) {
        Claims claims = parseMfaToken(request.mfaToken());
        return tx.execute(status -> {
            User user = userRepository.findById(UUID.fromString(claims.getSubject()))
                    .orElseThrow(() -> ApiException.unauthorized(INVALID_CREDENTIALS));
            if (user.getMfaEnabled()) {
                throw ApiException.conflict("ESG-409-MFA", "MFA is already enabled; ask an administrator to reset it");
            }
            String secret = totp.generateSecret();
            user.setMfaSecret(encryptor.encrypt(secret));
            userRepository.save(user);
            return new MfaRequests.SetupResponse(secret, totp.otpauthUri(MFA_ISSUER, user.getEmail(), secret));
        });
    }

    /** Verifies a TOTP code, enables MFA on first success, and issues tokens. */
    public AuthResult verifyMfa(MfaRequests.Verify request, ClientInfo client) {
        Claims claims = parseMfaToken(request.mfaToken());
        Object outcome = tx.execute(status -> {
            User user = userRepository.findById(UUID.fromString(claims.getSubject())).orElse(null);
            if (user == null || !user.getIsActive() || isLocked(user)) {
                return INVALID_CREDENTIALS;
            }
            if (user.getMfaSecret() == null) {
                throw ApiException.badRequest("ESG-400-MFA", "MFA is not set up");
            }
            if (!totp.verify(encryptor.decrypt(user.getMfaSecret()), request.code())) {
                registerFailure(user);
                return "Invalid verification code";
            }
            user.setMfaEnabled(true);
            return completeLogin(user, accessService.load(user.getId()), client);
        });
        if (outcome instanceof String error) {
            throw ApiException.unauthorized(error);
        }
        return (AuthResult) outcome;
    }

    public AuthResult refresh(String refreshToken, ClientInfo client) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(refreshToken, client.userAgent(), client.ip());
        TenantContext.setUserId(rotation.userId().toString());
        AuthResponse body = tx.execute(status -> {
            User user = userRepository.findById(rotation.userId()).orElse(null);
            UserAccess access = accessService.load(rotation.userId());
            if (user == null || !access.active()) {
                throw ApiException.unauthorized("Invalid or expired refresh token");
            }
            return tokensFor(user, access);
        });
        return new AuthResult(body, rotation.refreshToken());
    }

    public void logout(String refreshToken) {
        if (refreshToken != null) {
            refreshTokens.revoke(refreshToken);
        }
    }

    public AuthResponse.UserDto me() {
        UserAccess access = CurrentUser.access();
        User user = userRepository.findById(access.userId()).orElseThrow(() -> ApiException.notFound("User"));
        return userDto(user, access);
    }

    // ------------------------------------------------------------------ internals

    private AuthResult completeLogin(User user, UserAccess access, ClientInfo client) {
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        String refresh = refreshTokens.issue(user.getId(), user.getTenantId(), client.userAgent(), client.ip());
        return new AuthResult(tokensFor(user, access), refresh);
    }

    private AuthResponse tokensFor(User user, UserAccess access) {
        String accessToken = tokenProvider.generateAccessToken(user.getId().toString(), user.getTenantId().toString(),
                access.roles(), access.permissions());
        return AuthResponse.tokens(accessToken, tokenProvider.getJwtExpirationMs() / 1000, userDto(user, access));
    }

    private AuthResponse.UserDto userDto(User user, UserAccess access) {
        String tenantName = jdbc.query("select name from auth.tenants where id = ?",
                rs -> rs.next() ? rs.getString(1) : null, user.getTenantId());
        return new AuthResponse.UserDto(user.getId().toString(), user.getEmail(), user.getFirstName(),
                user.getLastName(), user.getTenantId().toString(), tenantName,
                access.roles().stream().sorted().toList(), access.permissions().stream().sorted().toList(),
                access.tenantWide(), access.orgScopeRoots().stream().map(UUID::toString).sorted().toList(),
                Boolean.TRUE.equals(user.getMfaEnabled()), user.getTimezone(), user.getPreferredLanguage(),
                access.modules().stream().sorted().toList());
    }

    private void registerFailure(User user) {
        user.setFailedLoginCount(user.getFailedLoginCount() + 1);
        if (user.getFailedLoginCount() >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plusSeconds(LOCK_SECONDS));
        }
        userRepository.save(user);
    }

    private static boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
    }

    private boolean requiresMfa(Set<String> roles) {
        if (roles.isEmpty()) {
            return false;
        }
        List<String> mfaRoles = jdbc.queryForList("select name from auth.system_roles where mfa_required", String.class);
        return roles.stream().anyMatch(mfaRoles::contains);
    }

    private String mfaToken(User user) {
        return tokenProvider.generateMfaToken(user.getId().toString(), user.getTenantId().toString());
    }

    private Claims parseMfaToken(String token) {
        Claims claims = tokenProvider.parseMfaToken(token)
                .orElseThrow(() -> ApiException.unauthorized("MFA session expired; sign in again"));
        setContext(UUID.fromString(claims.get("tenant_id", String.class)), UUID.fromString(claims.getSubject()));
        return claims;
    }

    private static void setContext(UUID tenantId, UUID userId) {
        TenantContext.setTenantId(tenantId.toString());
        TenantContext.setUserId(userId.toString());
    }

    private LoginIdentity findLoginIdentity(String email) {
        List<LoginIdentity> rows = jdbc.query(
                "select user_id, tenant_id from auth.find_login_identity(?)",
                (rs, i) -> new LoginIdentity(rs.getObject("user_id", UUID.class), rs.getObject("tenant_id", UUID.class)),
                email);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private record LoginIdentity(UUID userId, UUID tenantId) {}
}
