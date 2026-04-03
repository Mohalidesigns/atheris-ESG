package com.esg.service;

import com.esg.model.dto.AuthResponse;
import com.esg.model.dto.LoginRequest;
import com.esg.model.entity.User;
import com.esg.model.entity.UserRole;
import com.esg.repository.UserRepository;
import com.esg.repository.UserRoleRepository;
import com.esg.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    public AuthService(UserRepository userRepository, UserRoleRepository userRoleRepository, JwtTokenProvider tokenProvider, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!user.getIsActive()) {
            throw new RuntimeException("Account is disabled");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new RuntimeException("Account is locked. Try again later.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            if (user.getFailedLoginCount() >= 5) {
                user.setLockedUntil(Instant.now().plusSeconds(1800)); // 30 min lock
            }
            userRepository.save(user);
            throw new RuntimeException("Invalid email or password");
        }

        // Reset failed count on success
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Get user roles and permissions
        List<UserRole> userRoles = userRoleRepository.findByUserId(user.getId());
        String roleName = userRoles.isEmpty() ? "USER" : userRoles.get(0).getRole().getName();

        List<String> permissions = userRoles.stream()
                .flatMap(ur -> ur.getRole().getPermissions().stream())
                .map(p -> p.getResource() + ":" + p.getAction())
                .distinct()
                .toList();

        String accessToken = tokenProvider.generateAccessToken(
                user.getId(), user.getTenantId(), roleName, permissions);
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        return new AuthResponse(accessToken, refreshToken, "Bearer",
                tokenProvider.getJwtExpirationMs() / 1000,
                new AuthResponse.UserDto(user.getId(), user.getEmail(), user.getFirstName(),
                        user.getLastName(), user.getTenantId(), roleName, permissions,
                        user.getTimezone(), user.getPreferredLanguage()));
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        String userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<UserRole> userRoles = userRoleRepository.findByUserId(user.getId());
        String roleName = userRoles.isEmpty() ? "USER" : userRoles.get(0).getRole().getName();

        List<String> permissions = userRoles.stream()
                .flatMap(ur -> ur.getRole().getPermissions().stream())
                .map(p -> p.getResource() + ":" + p.getAction())
                .distinct()
                .toList();

        String newAccessToken = tokenProvider.generateAccessToken(
                user.getId(), user.getTenantId(), roleName, permissions);
        String newRefreshToken = tokenProvider.generateRefreshToken(user.getId());

        return new AuthResponse(newAccessToken, newRefreshToken, "Bearer",
                tokenProvider.getJwtExpirationMs() / 1000,
                new AuthResponse.UserDto(user.getId(), user.getEmail(), user.getFirstName(),
                        user.getLastName(), user.getTenantId(), roleName, permissions, null, null));
    }
}
