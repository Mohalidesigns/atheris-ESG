package com.esg.privacy;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** WP-52 NDPA 2023 API bodies (D40). The frontend Zod schemas (features/admin/privacySchemas.js) mirror these. */
public final class PrivacyDtos {
    private PrivacyDtos() {}

    public static final String KIND_REGEX = "^(access|rectification|erasure|objection|portability)$";

    public record NoticeRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 20000) String body,
            @NotBlank @Size(max = 300) String dpoContact) {}

    public record Notice(UUID id, int version, String title, String body, String dpoContact, Instant publishedAt, String publishedByName,
                         int acknowledged, int activeUsers) {}

    /** For the signed-in user: the notice in force and whether they have acknowledged it. */
    public record MyNotice(Notice notice, boolean acknowledged, Instant acknowledgedAt) {}

    public record AcknowledgeRequest(@NotNull @Min(1) Integer version) {}

    public record RequestCreate(
            @NotBlank @Pattern(regexp = KIND_REGEX, message = "must be access, rectification, erasure, objection or portability") String kind,
            UUID subjectUserId,
            @NotBlank @Size(max = 200) String subjectName,
            @Email @Size(max = 255) String subjectEmail,
            @Size(max = 4000) String details,
            @NotNull LocalDate receivedOn) {}

    public record RequestAction(
            @NotBlank @Pattern(regexp = "^(start|complete|refuse)$", message = "must be start, complete or refuse") String action,
            @Size(max = 4000) String response,
            @NotNull Long version) {}

    public record SubjectRequest(UUID id, String reference, String kind, UUID subjectUserId, String subjectName, String subjectEmail, String details,
                                 LocalDate receivedOn, LocalDate dueOn, boolean overdue, String status, String response, Instant completedAt,
                                 String handledByName, long version) {}

    public record RetentionSettings(
            @NotNull @Min(30) @Max(3650) Integer notificationsDays,
            @NotNull @Min(7) @Max(3650) Integer deliveryLogDays,
            @NotNull @Min(1) @Max(365) Integer sessionsDays,
            @NotNull @Min(7) @Max(3650) Integer accessLogIpDays,
            Long version) {}

    public record RetentionRun(int tenants, int notifications, int deliveries, int sessions, int accessLogIps) {}
}
