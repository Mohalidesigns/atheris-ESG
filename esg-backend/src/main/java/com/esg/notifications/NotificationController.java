package com.esg.notifications;

import com.esg.model.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's notifications and preferences (TRD §6.4.2 Notifications). */
@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('notifications:read')")
    public ResponseEntity<ApiResponse<NotificationDtos.NotificationPage>> inbox(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.inbox(unreadOnly, page, size)));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('notifications:read')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("unread", service.unreadCount())));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasAuthority('notifications:read')")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable UUID id) {
        service.markRead(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/read-all")
    @PreAuthorize("hasAuthority('notifications:read')")
    public ResponseEntity<ApiResponse<Void>> markAllRead() {
        service.markAllRead();
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/preferences")
    @PreAuthorize("hasAuthority('notifications:read')")
    public ResponseEntity<ApiResponse<List<NotificationDtos.Preference>>> preferences() {
        return ResponseEntity.ok(ApiResponse.ok(service.preferences()));
    }

    @PutMapping("/preferences")
    @PreAuthorize("hasAuthority('notifications:read')")
    public ResponseEntity<ApiResponse<List<NotificationDtos.Preference>>> updatePreferences(
            @Valid @RequestBody NotificationDtos.PreferencesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.updatePreferences(request.preferences()), "Preferences saved"));
    }
}
