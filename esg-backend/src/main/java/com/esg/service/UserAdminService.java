package com.esg.service;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.model.dto.AdminDtos.CreateUserRequest;
import com.esg.model.dto.AdminDtos.PageResponse;
import com.esg.model.dto.AdminDtos.ReplaceRolesRequest;
import com.esg.model.dto.AdminDtos.RoleAssignmentRequest;
import com.esg.model.dto.AdminDtos.RoleAssignmentResponse;
import com.esg.model.dto.AdminDtos.RoleResponse;
import com.esg.model.dto.AdminDtos.UpdateUserRequest;
import com.esg.model.dto.AdminDtos.UserResponse;
import com.esg.model.entity.User;
import com.esg.repository.UserRepository;
import com.esg.security.CurrentUser;
import com.esg.security.OrgScopeService;
import com.esg.security.PasswordPolicy;
import com.esg.security.RefreshTokenService;
import com.esg.security.TenantContext;
import com.esg.security.UserAccess;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin Console user and role management (WP-13, TRD §5.5). All queries run under RLS for the caller's tenant.
 *
 * <p>Role assignment rules (TRD §5.5): an assigner can only grant roles whose permissions they hold themselves
 * (no privilege escalation); org-scoped roles need an organisation the assigner can access; expiry must be in
 * the future; admins can't remove their own ability to assign roles.
 */
@Service
public class UserAdminService {

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final OrgScopeService orgScope;
    private final RefreshTokenService refreshTokens;

    private final com.esg.notifications.NotificationService notifications;

    public UserAdminService(UserRepository users, JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                            PasswordPolicy passwordPolicy, OrgScopeService orgScope, RefreshTokenService refreshTokens,
                            com.esg.notifications.NotificationService notifications) {
        this.notifications = notifications;
        this.users = users;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.orgScope = orgScope;
        this.refreshTokens = refreshTokens;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, int page, int size) {
        return list(search, null, null, page, size);
    }

    /** Sorted by first name, then last name (Phase 2); optional role and status (active | inactive) filters. */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, String role, String status, int page, int size) {
        if (status != null && !status.isBlank() && !status.matches("^(active|inactive)$")) {
            throw com.esg.exception.FieldValidationException.of("status", "must be active or inactive");
        }
        String q = search == null || search.isBlank() ? "" : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        Page<User> result = users.search(TenantContext.requireTenantId(), q, CurrentUser.access().platform(),
                role == null ? "" : role.trim(), status == null ? "" : status.trim(), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        Map<UUID, List<RoleAssignmentResponse>> roles = assignments(result.getContent().stream().map(User::getId).toList());
        return new PageResponse<>(result.getContent().stream().map(u -> toResponse(u, roles)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        User user = find(id);
        return toResponse(user, assignments(List.of(id)));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        List<String> problems = passwordPolicy.violations(request.password(), request.email());
        if (!problems.isEmpty()) {
            throw FieldValidationException.of("password", "Password needs " + String.join(", ", problems));
        }
        boolean taken = exists("select 1 from auth.find_login_identity(?)", request.email());
        if (taken) {
            throw FieldValidationException.of("email", "This email is already in use");
        }
        UUID tenantId = TenantContext.requireTenantId();
        Integer maxUsers = jdbc.queryForObject("select max_users from auth.tenants where id = ?", Integer.class, tenantId);
        if (maxUsers != null && users.countSeats(tenantId) >= maxUsers) {
            throw ApiException.conflict("ESG-409-SEATS", "Your subscription allows " + maxUsers + " active users");
        }
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(blankToNull(request.phone()));
        user.setPreferredLanguage(request.preferredLanguage() == null ? "en" : request.preferredLanguage());
        user.setTimezone(request.timezone() == null || request.timezone().isBlank() ? "Africa/Lagos" : request.timezone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPasswordChangedAt(Instant.now());
        User saved = users.saveAndFlush(user);
        notifications.notifyAfterCommit(saved.getId(), "WELCOME", Map.of());
        return toResponse(saved, Map.of());
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = find(id);
        if (!user.getVersion().equals(request.version())) {
            throw ApiException.conflict("ESG-409-VERSION", "This user was changed by someone else. Reload and try again.");
        }
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(blankToNull(request.phone()));
        if (request.preferredLanguage() != null) {
            user.setPreferredLanguage(request.preferredLanguage());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            user.setTimezone(request.timezone());
        }
        User saved = users.saveAndFlush(user);
        return toResponse(saved, assignments(List.of(id)));
    }

    /** Deactivates a user and ends all their sessions; they lose API access on the next request. */
    @Transactional
    public UserResponse deactivate(UUID id) {
        if (id.equals(CurrentUser.access().userId())) {
            throw ApiException.badRequest("ESG-400-SELF", "You can't deactivate your own account");
        }
        User user = find(id);
        user.setIsActive(false);
        User saved = users.saveAndFlush(user);
        refreshTokens.revokeAllSessions(id);
        return toResponse(saved, assignments(List.of(id)));
    }

    /** Clears a user's MFA enrolment (lost device) and ends their sessions; they enrol again at next sign-in. */
    @Transactional
    public UserResponse resetMfa(UUID id) {
        User user = find(id);
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        User saved = users.saveAndFlush(user);
        refreshTokens.revokeAllSessions(id);
        return toResponse(saved, assignments(List.of(id)));
    }

    /** Sets a new password chosen by an administrator, unlocks the account and ends all sessions. */
    @Transactional
    public UserResponse setPassword(UUID id, String password) {
        User user = find(id);
        List<String> problems = passwordPolicy.violations(password, user.getEmail());
        if (!problems.isEmpty()) {
            throw FieldValidationException.of("password", "Password needs " + String.join(", ", problems));
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordChangedAt(Instant.now());
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        User saved = users.saveAndFlush(user);
        refreshTokens.revokeAllSessions(id);
        notifications.notifyAfterCommit(id, "PASSWORD_SET_BY_ADMIN", Map.of());
        return toResponse(saved, assignments(List.of(id)));
    }

    @Transactional(readOnly = true)
    public List<RoleAssignmentResponse> getRoles(UUID userId) {
        find(userId);
        return assignments(List.of(userId)).getOrDefault(userId, List.of());
    }

    @Transactional
    public List<RoleAssignmentResponse> replaceRoles(UUID userId, ReplaceRolesRequest request) {
        find(userId);
        UserAccess assigner = CurrentUser.access();
        Map<UUID, RoleInfo> roles = roleInfo();
        Map<String, String> fieldErrors = new HashMap<>();
        Set<String> newPermissions = new HashSet<>();

        for (int i = 0; i < request.assignments().size(); i++) {
            RoleAssignmentRequest a = request.assignments().get(i);
            String prefix = "assignments[" + i + "].";
            RoleInfo role = roles.get(a.roleId());
            if (role == null) {
                fieldErrors.put(prefix + "roleId", "Unknown role");
                continue;
            }
            if (!assigner.permissions().containsAll(role.permissions())) {
                throw ApiException.forbidden("ESG-403-ROLE", "You can't assign " + role.name() + ": it has permissions you don't hold");
            }
            if ("org".equals(role.scopeType()) && a.orgScopeId() == null) {
                fieldErrors.put(prefix + "orgScopeId", role.name() + " must be limited to an organisation");
            }
            if (a.orgScopeId() != null) {
                if (!exists("select 1 from auth.organisations where id = ?", a.orgScopeId())) {
                    fieldErrors.put(prefix + "orgScopeId", "Unknown organisation");
                } else {
                    orgScope.requireAccess(a.orgScopeId());
                }
            }
            newPermissions.addAll(role.permissions());
        }
        if (!fieldErrors.isEmpty()) {
            throw new FieldValidationException(fieldErrors);
        }
        if (userId.equals(assigner.userId()) && assigner.has("roles:assign") && !newPermissions.contains("roles:assign")) {
            throw ApiException.badRequest("ESG-400-SELF", "You can't remove your own ability to assign roles");
        }

        jdbc.update("delete from auth.user_roles where user_id = ?", userId);
        for (RoleAssignmentRequest a : request.assignments()) {
            jdbc.update("insert into auth.user_roles (user_id, role_id, org_scope_id, granted_by, expires_at) "
                            + "values (?, ?, ?, ?, ?) on conflict do nothing",
                    userId, a.roleId(), a.orgScopeId(), assigner.userId(),
                    a.expiresAt() == null ? null : Timestamp.from(a.expiresAt()));
        }
        List<RoleAssignmentResponse> result = assignments(List.of(userId)).getOrDefault(userId, List.of());
        String roleNames = result.isEmpty() ? "none" : String.join(", ", result.stream().map(r -> r.roleName().replace('_', ' ')).toList());
        notifications.notifyAfterCommit(userId, "ROLES_CHANGED", Map.of("roles", roleNames));
        return result;
    }

    /**
     * Roles visible to the caller (S4). Platform-scope roles (SUPER_ADMIN) exist only for platform users. Each role
     * says whether the caller may assign it (holds all its permissions); {@code assignableOnly} returns just those,
     * which is what the role picker offers. {@link #replaceRoles} enforces the same rule regardless.
     */
    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles(boolean assignableOnly) {
        UserAccess caller = CurrentUser.access();
        Map<UUID, RoleInfo> info = roleInfo();
        return info.values().stream()
                .filter(r -> caller.platform() || !"platform".equals(r.scopeType()))
                .filter(r -> !assignableOnly || caller.permissions().containsAll(r.permissions()))
                .sorted(java.util.Comparator.comparing(RoleInfo::sortOrder).thenComparing(RoleInfo::name))
                .map(r -> new RoleResponse(r.id(), r.name(), r.description(), r.scopeType(), r.mfaRequired(),
                        r.systemRole(), r.permissions().stream().sorted().toList(),
                        caller.permissions().containsAll(r.permissions())))
                .toList();
    }

    // ------------------------------------------------------------------ internals

    private record RoleInfo(UUID id, String name, String description, String scopeType, boolean mfaRequired,
                            boolean systemRole, int sortOrder, Set<String> permissions) {}

    private Map<UUID, RoleInfo> roleInfo() {
        Map<UUID, RoleInfo> roles = new HashMap<>();
        jdbc.query("select r.id, r.name, r.description, coalesce(sr.scope_type, 'tenant'), coalesce(sr.mfa_required, false), "
                        + "r.is_system_role, coalesce(sr.sort_order, 999) "
                        + "from auth.roles r left join auth.system_roles sr on sr.name = r.name and r.is_system_role",
                rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    roles.put(id, new RoleInfo(id, rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5),
                            rs.getBoolean(6), rs.getInt(7), new HashSet<>()));
                });
        jdbc.query("select rp.role_id, p.resource || ':' || p.action from auth.role_permissions rp "
                        + "join auth.permissions p on p.id = rp.permission_id",
                rs -> {
                    RoleInfo role = roles.get(rs.getObject(1, UUID.class));
                    if (role != null) {
                        role.permissions().add(rs.getString(2));
                    }
                });
        return roles;
    }

    private Map<UUID, List<RoleAssignmentResponse>> assignments(List<UUID> userIds) {
        Map<UUID, List<RoleAssignmentResponse>> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        jdbc.query(con -> {
            var ps = con.prepareStatement("select ur.user_id, ur.id, ur.role_id, r.name, ur.org_scope_id, o.name, "
                    + "ur.expires_at, ur.granted_at from auth.user_roles ur join auth.roles r on r.id = ur.role_id "
                    + "left join auth.organisations o on o.id = ur.org_scope_id where ur.user_id = any(?) order by r.name");
            ps.setArray(1, con.createArrayOf("uuid", userIds.toArray()));
            return ps;
        }, rs -> {
            Timestamp expires = rs.getTimestamp(7);
            result.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(new RoleAssignmentResponse(
                    rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getString(4),
                    rs.getObject(5, UUID.class), rs.getString(6), expires == null ? null : expires.toInstant(),
                    rs.getTimestamp(8).toInstant()));
        });
        return result;
    }

    private boolean exists(String sql, Object arg) {
        return !jdbc.queryForList(sql, Integer.class, arg).isEmpty();
    }

    /** Platform-scope users don't exist for tenant administrators (S5): same 404 as an unknown id. */
    private User find(UUID id) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (!CurrentUser.access().platform() && users.isPlatformUser(id)) {
            throw ApiException.notFound("User");
        }
        return user;
    }

    private static UserResponse toResponse(User u, Map<UUID, List<RoleAssignmentResponse>> roles) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getPhone(),
                u.getPreferredLanguage(), u.getTimezone(), Boolean.TRUE.equals(u.getIsActive()),
                Boolean.TRUE.equals(u.getMfaEnabled()), u.getLastLoginAt(), u.getCreatedAt(), u.getVersion(),
                roles.getOrDefault(u.getId(), List.of()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
