package com.esg.service;

import com.esg.model.dto.AuthResponse;
import com.esg.model.dto.LoginRequest;
import com.esg.model.entity.User;
import com.esg.model.entity.UserRole;
import com.esg.repository.UserRepository;
import com.esg.repository.UserRoleRepository;
import com.esg.security.JwtTokenProvider;
import com.esg.security.TenantContext;
import io.jsonwebtoken.Claims;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    public AuthService(UserRepository userRepository, UserRoleRepository userRoleRepository,
                       JwtTokenProvider tokenProvider, PasswordEncoder passwordEncoder,
                       JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * The tenant isn't known before login and auth.users is protected by RLS, so the email is first
     * resolved to (user, tenant) through auth.find_login_identity() (V002). Everything after that runs
     * inside the user's tenant. The failed-attempt counter is committed before the error is thrown, so
     * lockout actually persists.
     */
    public AuthResponse login(LoginRequest request) {
        LoginIdentity identity = findLoginIdentity(request.getEmail());
        if (identity == null) {
            throw new RuntimeException(INVALID_CREDENTIALS);
        }
        TenantContext.setTenantId(identity.tenantId().toString());
        TenantContext.setUserId(identity.userId().toString());

        LoginOutcome outcome = transactionTemplate.execute(status -> attemptLogin(identity.userId(), request.getPassword()));
        if (outcome.error() != null) {
            throw new RuntimeException(outcome.error());
        }
        return outcome.response();
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("Invalid or expired refresh token");
        }
        Claims claims = tokenProvider.parseToken(refreshToken);
        String tenantId = claims.get("tenant_id", String.class);
        if (tenantId == null) {
            throw new RuntimeException("Invalid or expired refresh token");
        }
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(claims.getSubject());

        return transactionTemplate.execute(status -> {
            User user = userRepository.findById(UUID.fromString(claims.getSubject()))
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return buildResponse(user, false);
        });
    }

    private LoginOutcome attemptLogin(UUID userId, String password) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return LoginOutcome.failure(INVALID_CREDENTIALS);
        }
        if (!user.getIsActive()) {
            return LoginOutcome.failure("Account is disabled");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            return LoginOutcome.failure("Account is locked. Try again later.");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            if (user.getFailedLoginCount() >= 5) {
                user.setLockedUntil(Instant.now().plusSeconds(1800)); // 30 min lock
            }
            userRepository.save(user);
            return LoginOutcome.failure(INVALID_CREDENTIALS);
        }

        // Reset failed count on success
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        return LoginOutcome.success(buildResponse(user, true));
    }

    private AuthResponse buildResponse(User user, boolean includePreferences) {
        List<UserRole> userRoles = userRoleRepository.findByUserId(user.getId());
        String roleName = userRoles.isEmpty() ? "USER" : userRoles.get(0).getRole().getName();

        List<String> permissions = userRoles.stream()
                .flatMap(ur -> ur.getRole().getPermissions().stream())
                .map(p -> p.getResource() + ":" + p.getAction())
                .distinct()
                .toList();

        String userId = user.getId().toString();
        String tenantId = user.getTenantId().toString();
        String accessToken = tokenProvider.generateAccessToken(userId, tenantId, roleName, permissions);
        String refreshToken = tokenProvider.generateRefreshToken(userId, tenantId);

        return new AuthResponse(accessToken, refreshToken, "Bearer",
                tokenProvider.getJwtExpirationMs() / 1000,
                new AuthResponse.UserDto(userId, user.getEmail(), user.getFirstName(),
                        user.getLastName(), tenantId, roleName, permissions,
                        includePreferences ? user.getTimezone() : null,
                        includePreferences ? user.getPreferredLanguage() : null));
    }

    private LoginIdentity findLoginIdentity(String email) {
        List<LoginIdentity> rows = jdbcTemplate.query(
                "select user_id, tenant_id from auth.find_login_identity(?)",
                (rs, i) -> new LoginIdentity(rs.getObject("user_id", UUID.class), rs.getObject("tenant_id", UUID.class)),
                email);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private record LoginIdentity(UUID userId, UUID tenantId) {}

    private record LoginOutcome(AuthResponse response, String error) {
        static LoginOutcome success(AuthResponse response) { return new LoginOutcome(response, null); }
        static LoginOutcome failure(String error) { return new LoginOutcome(null, error); }
    }
}
