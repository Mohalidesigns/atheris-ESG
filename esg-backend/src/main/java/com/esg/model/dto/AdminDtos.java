package com.esg.model.dto;

import com.esg.security.FieldPermission;
import com.esg.security.PasswordPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin Console request/response bodies (WP-13). Validation rules here are mirrored exactly by the frontend
 * Zod schemas in src/features/admin/schemas.js (CLAUDE.md rule 5). Password strength is checked by
 * {@link PasswordPolicy} in the service and reported against the {@code password} field.
 */
public final class AdminDtos {
    private AdminDtos() {}

    public static final String PHONE_REGEX = "^\\+?[0-9]{7,15}$";
    public static final String LANGUAGE_REGEX = "^(en|fr|pt|ar|sw|ha|yo)$";

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 20) @Pattern(regexp = PHONE_REGEX, message = "must be 7-15 digits, optionally starting with +") String phone,
            @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String password,
            @Pattern(regexp = LANGUAGE_REGEX, message = "must be one of en, fr, pt, ar, sw, ha, yo") String preferredLanguage,
            @Size(max = 50) String timezone) {}

    public record UpdateUserRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 20) @Pattern(regexp = PHONE_REGEX, message = "must be 7-15 digits, optionally starting with +") String phone,
            @Pattern(regexp = LANGUAGE_REGEX, message = "must be one of en, fr, pt, ar, sw, ha, yo") String preferredLanguage,
            @Size(max = 50) String timezone,
            @NotNull Long version) {}

    public record RoleAssignmentRequest(@NotNull UUID roleId, UUID orgScopeId, @Future Instant expiresAt) {}

    public record ReplaceRolesRequest(@NotNull @Size(max = 20) List<@Valid @NotNull RoleAssignmentRequest> assignments) {}

    public record RoleAssignmentResponse(UUID id, UUID roleId, String roleName, UUID orgScopeId, String orgScopeName,
                                         Instant expiresAt, Instant grantedAt) {}

    public record UserResponse(UUID id, String email, String firstName, String lastName,
                               @FieldPermission("users:read_pii") String phone,
                               String preferredLanguage, String timezone, boolean active, boolean mfaEnabled,
                               Instant lastLoginAt, Instant createdAt, Long version,
                               List<RoleAssignmentResponse> roles) {}

    public record RoleResponse(UUID id, String name, String description, String scopeType, boolean mfaRequired,
                               boolean systemRole, List<String> permissions) {}

    public record OrganisationOption(UUID id, String name, UUID parentOrgId, String orgType) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
}
