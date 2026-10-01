package com.esg.controller;

import com.esg.model.dto.AdminDtos.CreateUserRequest;
import com.esg.model.dto.AdminDtos.PageResponse;
import com.esg.model.dto.AdminDtos.ReplaceRolesRequest;
import com.esg.model.dto.AdminDtos.RoleAssignmentResponse;
import com.esg.model.dto.AdminDtos.UpdateUserRequest;
import com.esg.model.dto.AdminDtos.UserResponse;
import com.esg.model.dto.ApiResponse;
import com.esg.model.dto.TenancyDtos;
import com.esg.service.UserAdminService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin Console: users and role assignments (TRD §6.4.2 Users, §5.5). */
@RestController
@RequestMapping("/v1/admin/users")
public class AdminUserController {

    private final UserAdminService service;

    public AdminUserController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('users:read')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(search, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('users:read')")
    public ResponseEntity<ApiResponse<UserResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('users:create')")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(request), "User created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('users:update')")
    public ResponseEntity<ApiResponse<UserResponse>> update(@PathVariable UUID id,
                                                            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, request), "User updated"));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('users:delete')")
    public ResponseEntity<ApiResponse<UserResponse>> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.deactivate(id), "User deactivated"));
    }

    @PostMapping("/{id}/reset-mfa")
    @PreAuthorize("hasAuthority('users:update')")
    public ResponseEntity<ApiResponse<UserResponse>> resetMfa(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.resetMfa(id), "MFA reset; the user enrols again at next sign-in"));
    }

    @PostMapping("/{id}/password")
    @PreAuthorize("hasAuthority('users:update')")
    public ResponseEntity<ApiResponse<UserResponse>> setPassword(@PathVariable UUID id,
                                                                 @Valid @RequestBody TenancyDtos.SetPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.setPassword(id, request.password()), "Password set; all sessions ended"));
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('users:read')")
    public ResponseEntity<ApiResponse<List<RoleAssignmentResponse>>> roles(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getRoles(id)));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('roles:assign')")
    public ResponseEntity<ApiResponse<List<RoleAssignmentResponse>>> replaceRoles(
            @PathVariable UUID id, @Valid @RequestBody ReplaceRolesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.replaceRoles(id, request), "Roles updated"));
    }
}
