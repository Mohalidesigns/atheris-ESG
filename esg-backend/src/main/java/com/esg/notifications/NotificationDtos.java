package com.esg.notifications;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Notification API bodies (WP-15). */
public final class NotificationDtos {
    private NotificationDtos() {}

    public record InAppNotification(UUID id, String type, String title, String message, String link, boolean read,
                                    Instant createdAt) {}

    public record NotificationPage(List<InAppNotification> content, long unread, int page, int size, long totalElements) {}

    /** One preference toggle. {@code sandbox} marks channels backed by a sandbox adapter (CLAUDE.md rule 9). */
    public record Preference(String type, String channel, boolean enabled, boolean available, boolean sandbox) {}

    public record PreferenceUpdate(
            @NotBlank @Size(max = 100) String type,
            @NotBlank @Pattern(regexp = "^(in_app|email|sms)$", message = "must be in_app, email or sms") String channel,
            @NotNull Boolean enabled) {}

    public record PreferencesRequest(@NotNull @Size(max = 100) List<@Valid @NotNull PreferenceUpdate> preferences) {}
}
