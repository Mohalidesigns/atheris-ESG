package com.esg.controller;

import com.esg.model.dto.AdminDtos.RoleResponse;
import com.esg.model.dto.ApiResponse;
import com.esg.service.UserAdminService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin Console lookups: roles with their permissions (organisations: OrganisationController). */
@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final UserAdminService service;

    public AdminController(UserAdminService service) {
        this.service = service;
    }

    /** {@code assignable=true}: only roles the caller may grant (the role picker; S4). */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('roles:read')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> roles(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean assignable) {
        return ResponseEntity.ok(ApiResponse.ok(service.listRoles(assignable)));
    }
}
