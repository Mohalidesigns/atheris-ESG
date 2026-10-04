package com.esg.compliance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for the Regulatory Compliance Manager (WP-22; TRD §6.4.2, §8.3). */
public final class ComplianceDtos {

    private ComplianceDtos() {}

    // ---------------------------------------------------------------- gap analysis

    public record AttestationView(String status, String notes, List<UUID> evidenceFileIds, String attestedBy, Instant attestedAt,
                                  Long version) {}

    public record RequirementGap(UUID id, String code, String title, String category, String parentCode, boolean leaf, boolean quantitative,
                                 String status,
                                 String basis, BigDecimal completeness, List<String> metricKeys, List<String> missingMetrics,
                                 String satisfiedBy, Integer priorityScore, String priority, String recommendation,
                                 AttestationView attestation) {}

    public record GapReport(String framework, String frameworkName, String frameworkType, String contentStatus, int reportingYear,
                            int totalRequirements, int met, int partial, int missing, int notApplicable, BigDecimal overallCompleteness,
                            int highPriority, List<RequirementGap> requirements) {}

    public record NextDeadline(UUID id, String description, LocalDate deadlineDate, long daysLeft, String status) {}

    public record FrameworkStatus(String code, String name, String jurisdiction, String frameworkType, String contentStatus,
                                  boolean active, int totalRequirements, int met, int partial, int missing, int notApplicable,
                                  BigDecimal completeness, int highPriority, NextDeadline nextDeadline) {}

    public record ActiveFrameworksRequest(@NotNull @Size(max = 20) List<@NotBlank String> codes) {}

    public record AttestationRequest(
            @NotBlank String framework,
            @NotBlank String requirementCode,
            @NotNull @Min(2000) @Max(2100) Integer year,
            @NotBlank @Pattern(regexp = "^(not_started|in_progress|complete|not_applicable)$",
                    message = "must be not_started, in_progress, complete or not_applicable") String status,
            @Size(max = 2000) String notes,
            @Size(max = 20) List<UUID> evidenceFileIds,
            Long version) {}

    public record MapRequest(@NotBlank String sourceFramework, @NotBlank String targetFramework,
                             @NotNull @Min(2000) @Max(2100) Integer year) {}

    public record MappedSource(String framework, String code, String title, String relation, String status) {}

    public record MappedRequirement(String code, String title, String status, String basis, String satisfiedBy, List<MappedSource> sources) {}

    public record MapResult(String sourceFramework, String targetFramework, int year, int mappedRequirements, int metViaSource,
                            List<MappedRequirement> requirements) {}

    // ---------------------------------------------------------------- filing deadlines

    public record DeadlineRequest(
            String framework,
            @NotBlank @Size(max = 500) String description,
            @NotNull LocalDate deadlineDate,
            @Min(2000) @Max(2100) Integer reportingYear,
            @NotNull UUID ownerUserId,
            @NotNull @Size(max = 10) List<@NotNull @Min(1) @Max(365) Integer> reminderDays,
            @Size(max = 1000) String notes,
            Long version) {}

    public record SubmitRequest(@NotNull Long version, @NotNull LocalDate submittedOn,
                                @NotBlank @Size(max = 100) String submissionRef, @Size(max = 20) List<UUID> evidenceFileIds) {}

    public record WaiveRequest(@NotNull Long version, @NotBlank @Size(max = 500) String reason) {}

    public record VersionRequest(@NotNull Long version) {}

    public record ReminderSent(String kind, int daysBefore, LocalDate sentOn, String recipient) {}

    public record DeadlineResponse(UUID id, String framework, String frameworkName, String description, LocalDate deadlineDate,
                                   Integer reportingYear, UUID ownerUserId, String ownerName, List<Integer> reminderDays, String status,
                                   long daysLeft, LocalDate submittedOn, String submissionRef, List<UUID> evidenceFileIds,
                                   String waivedReason, String notes, List<ReminderSent> reminders, Long version) {}

    public record ReminderRun(LocalDate date, int tenants, int remindersSent, int overdueMarked) {}

    public record Owner(UUID id, String name, String email) {}

    // ---------------------------------------------------------------- regulatory changes

    public record ChangeRequest(
            String framework,
            @NotBlank @Size(max = 100) String regulator,
            @NotBlank @Size(max = 255) String title,
            @Size(max = 4000) String summary,
            @Size(max = 500) @Pattern(regexp = "^(https?://\\S+)?$", message = "must be an http(s) link") String sourceUrl,
            @NotNull LocalDate publishedDate,
            LocalDate effectiveDate,
            LocalDate assessmentDue,
            @NotNull UUID ownerUserId,
            Long version) {}

    public record AssessRequest(
            @NotNull Long version,
            @NotBlank @Pattern(regexp = "^(high|medium|low|none)$", message = "must be high, medium, low or none") String impactLevel,
            @NotBlank @Size(max = 4000) String impactAssessment,
            @Size(max = 4000) String requiredActions) {}

    public record ChangeResponse(UUID id, String framework, String frameworkName, String regulator, String title, String summary,
                                 String sourceUrl, LocalDate publishedDate, LocalDate effectiveDate, LocalDate assessmentDue,
                                 UUID ownerUserId, String ownerName, String status, String impactLevel, String impactAssessment,
                                 String requiredActions, String assessedBy, Instant assessedAt, String closedBy, Instant closedAt,
                                 Instant createdAt, Long version, String closeBlockedReason) {}
}
