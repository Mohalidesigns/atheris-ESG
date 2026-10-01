package com.esg.reporting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request and response shapes for the Reporting & Disclosure Studio (WP-23; TRD §6.4.2, §6.5.3, §8.4). */
public final class ReportingDtos {

    private ReportingDtos() {}

    public record TemplateSection(String title, String type, String content) {}

    public record Template(UUID id, String key, String name, String frameworkCode, String description, String source, String contentStatus,
                           boolean platform, List<TemplateSection> sections) {}

    public record ReportRequest(
            @NotNull UUID templateId,
            @NotBlank @Size(max = 500) String title,
            @Size(max = 1000) String description,
            @NotNull LocalDate reportingPeriodStart,
            @NotNull LocalDate reportingPeriodEnd,
            Long version) {}

    public record ReportDetailsRequest(
            @NotBlank @Size(max = 500) String title,
            @Size(max = 1000) String description,
            @NotNull LocalDate reportingPeriodStart,
            @NotNull LocalDate reportingPeriodEnd,
            @NotNull Long version) {}

    public record SectionInput(
            UUID id,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Pattern(regexp = "^(narrative|data)$", message = "must be narrative or data") String type,
            @Size(max = 20000) String content) {}

    public record SectionsRequest(@NotNull Long version, @NotEmpty @Size(max = 50) List<@Valid SectionInput> sections) {}

    public record SubmitRequest(@NotNull Long version, @Size(max = 2000) String comment) {}

    public record StepActionRequest(
            @NotNull Long version,
            @NotBlank @Pattern(regexp = "^(approve|reject|request_changes)$", message = "must be approve, reject or request_changes") String action,
            @Size(max = 2000) String comments) {}

    public record GenerateRequest(@NotBlank @Pattern(regexp = "^(pdf|xlsx)$", message = "must be pdf or xlsx") String format) {}

    /** One resolved template variable, frozen in the report's snapshot. */
    public record VariableValue(String display, java.math.BigDecimal value, String unit, String status, String basis) {}

    public record Section(UUID id, String title, String type, String content, String rendered, int sortOrder) {}

    public record Step(UUID id, int stepNumber, String label, String role, String status, String approver, Instant actedAt, String comments,
                       Instant activatedAt, Instant dueAt, boolean overdue, boolean canAct) {}

    public record Approval(UUID workflowId, String status, int currentStep, int totalSteps, List<Step> steps) {}

    public record VersionSummary(UUID id, String label, String event, String comment, String createdBy, Instant createdAt) {}

    public record VersionDetail(UUID id, String label, String event, String comment, String createdBy, Instant createdAt,
                                List<Section> sections, Map<String, VariableValue> snapshot) {}

    public record ExportJob(UUID id, String versionLabel, String format, String status, UUID fileId, String error, String createdBy,
                            Instant createdAt, Instant finishedAt) {}

    public record ReportSummary(UUID id, String title, String frameworkCode, UUID templateId, String templateName, String status,
                                String versionLabel, LocalDate reportingPeriodStart, LocalDate reportingPeriodEnd, String nextApproval,
                                String createdBy, Instant updatedAt, Long version) {}

    public record ReportDetail(UUID id, String title, String description, String frameworkCode, UUID templateId, String templateName,
                               String status, String versionLabel, LocalDate reportingPeriodStart, LocalDate reportingPeriodEnd,
                               List<Section> sections, Map<String, VariableValue> snapshot, Instant snapshotAt, String submittedBy,
                               Instant submittedAt, Instant approvedAt, Approval approval, List<VersionSummary> versions,
                               List<ExportJob> exports, String createdBy, Instant createdAt, Long version) {}

    public record SlaRun(int tenants, int reminded) {}
}
