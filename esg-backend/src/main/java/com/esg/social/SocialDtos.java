package com.esg.social;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M7 Social Impact API bodies (WP-33, D31). The frontend Zod schemas (features/social/schemas.js) mirror these. */
public final class SocialDtos {
    private SocialDtos() {}

    public static final String LEVEL_REGEX = "^(executive|senior_management|middle_management|non_management)$";
    public static final String GENDER_REGEX = "^(female|male|undisclosed)$";
    public static final String AGE_BAND_REGEX = "^(under_30|30_50|over_50)$";
    public static final String CURRENCY_REGEX = "^(NGN|USD|EUR|GBP|KES|ZAR|GHS|XOF|EGP)$";
    public static final String INCIDENT_TYPE_REGEX = "^(injury|illness|near_miss|dangerous_occurrence|property_damage|environmental)$";
    public static final String OUTCOME_REGEX = "^(none|first_aid|medical_treatment|restricted_work|lost_time|fatality)$";
    public static final String PERSON_TYPE_REGEX = "^(employee|contractor|visitor|community_member)$";
    public static final String PROJECT_CATEGORY_REGEX = "^(education|health|water_sanitation|infrastructure|livelihoods|environment|"
            + "electrification|scholarships|other)$";
    public static final String PROJECT_STATUS_REGEX = "^(planning|active|on_hold|completed|cancelled)$";
    public static final String CHANNEL_REGEX = "^(in_person|phone|sms|email|letter|community_meeting|other)$";
    public static final String GRIEVANCE_CATEGORY_REGEX = "^(land_compensation|environment|employment|security|health_safety|"
            + "community_investment|cultural_heritage|other)$";
    public static final String SEVERITY_REGEX = "^(low|medium|high|critical)$";

    /** A workflow step: the action, and a note or reason where the step needs one. */
    public record ActionRequest(
            @NotBlank @Size(max = 30) String action,
            @Size(max = 2000) String note,
            UUID investigatorId,
            @Size(max = 4000) String rootCause,
            @Size(max = 4000) String correctiveActions,
            @Size(max = 4000) String resolution,
            @Pattern(regexp = "^(yes|no|unknown)$", message = "must be yes, no or unknown") String complainantSatisfied,
            @NotNull Long version) {}

    // ---------------------------------------------------------------- workforce

    public record WorkforceLineRequest(
            @NotBlank @Pattern(regexp = LEVEL_REGEX, message = "must be an employee level") String level,
            @NotBlank @Pattern(regexp = GENDER_REGEX, message = "must be female, male or undisclosed") String gender,
            @NotBlank @Pattern(regexp = AGE_BAND_REGEX, message = "must be an age band") String ageBand,
            @NotNull @Min(0) @Max(1_000_000) Integer headcount,
            @Min(0) @Max(1_000_000) Integer disabledHeadcount,
            @DecimalMin("0") @Digits(integer = 18, fraction = 2) BigDecimal totalAnnualPay) {}

    public record WorkforceSnapshotRequest(
            @NotNull UUID orgId,
            @NotNull LocalDate reportingPeriodStart,
            @NotNull LocalDate reportingPeriodEnd,
            @DecimalMin("0") @Digits(integer = 15, fraction = 1) BigDecimal hoursWorked,
            @Min(0) @Max(1_000_000) Integer newHires,
            @Min(0) @Max(1_000_000) Integer leavers,
            @Pattern(regexp = CURRENCY_REGEX, message = "must be one of NGN, USD, EUR, GBP, KES, ZAR, GHS, XOF, EGP") String payCurrency,
            @Size(max = 1000) String notes,
            @NotNull @Size(min = 1, max = 60) List<@Valid WorkforceLineRequest> lines,
            Long version) {}

    public record WorkforceLine(String level, String gender, String ageBand, int headcount, int disabledHeadcount, BigDecimal totalAnnualPay) {}

    public record WorkforceSnapshot(UUID id, UUID orgId, String orgName, LocalDate reportingPeriodStart, LocalDate reportingPeriodEnd,
                                    BigDecimal hoursWorked, Integer newHires, Integer leavers, String payCurrency, String notes,
                                    String status, String statusReason, String submittedByName, Instant submittedAt,
                                    String approvedByName, Instant approvedAt, List<WorkforceLine> lines, WorkforceMetrics.Result metrics,
                                    List<String> actions, Map<String, String> blockedActions, Long version) {}

    // ---------------------------------------------------------------- safety

    public record IncidentRequest(
            @NotNull UUID orgId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Pattern(regexp = INCIDENT_TYPE_REGEX, message = "must be an incident type") String incidentType,
            @NotBlank @Pattern(regexp = OUTCOME_REGEX, message = "must be an outcome") String outcome,
            @Pattern(regexp = PERSON_TYPE_REGEX, message = "must be employee, contractor, visitor or community member") String personType,
            @NotNull LocalDateTime occurredAt,
            @Min(0) @Max(3650) Integer lostDays,
            @Size(max = 255) String location,
            @Size(max = 4000) String description,
            Long version) {}

    public record Incident(UUID id, UUID orgId, String orgName, String title, String incidentType, String outcome, String personType,
                           @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime occurredAt, Integer lostDays, String location, String description, boolean recordable,
                           boolean lostTime, String status, String statusReason, UUID investigatorId, String investigatorName,
                           String rootCause, String correctiveActions, String findingsByName, Instant findingsAt, String closedByName,
                           Instant closedAt, String reportedByName, Instant createdAt, List<String> actions, Map<String, String> blockedActions,
                           Long version) {}

    public record YearRates(int year, BigDecimal hoursWorked, long lostTimeInjuries, long recordableCases, BigDecimal ltifr, BigDecimal trir) {}

    public record MonthCount(int month, long incidents, long recordable, long lostTime, long nearMisses) {}

    public record SafetyStatistics(int year, BigDecimal hoursWorked, int sitesWithHours, long lostTimeInjuries, long recordableCases,
                                   long fatalities, long lostDays, BigDecimal ltifr, BigDecimal trir, long excludedIncidents,
                                   long contractorRecordable, long nearMisses, long openInvestigations, List<YearRates> trend,
                                   List<MonthCount> byMonth) {}

    // ---------------------------------------------------------------- community

    public record ProjectRequest(
            @NotNull UUID orgId,
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Pattern(regexp = PROJECT_CATEGORY_REGEX, message = "must be a project category") String category,
            @Size(max = 2000) String description,
            @Size(max = 255) String community,
            @Size(max = 100) String stateRegion,
            @Size(max = 100) String lga,
            @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
            @DecimalMin("0") @Digits(integer = 18, fraction = 2) BigDecimal budget,
            @NotBlank @Pattern(regexp = CURRENCY_REGEX, message = "must be one of NGN, USD, EUR, GBP, KES, ZAR, GHS, XOF, EGP") String currency,
            @Min(0) @Max(100_000_000) Integer plannedBeneficiaries,
            @Min(0) @Max(100_000_000) Integer actualBeneficiaries,
            @NotBlank @Pattern(regexp = PROJECT_STATUS_REGEX, message = "must be a project status") String status,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 17) List<@NotNull @Min(1) @Max(17) Integer> sdgs,
            @NotNull Boolean piaHostCommunity,
            Long version) {}

    public record ExpenditureRequest(
            @NotNull LocalDate spentOn,
            @NotBlank @Size(max = 500) String description,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 18, fraction = 2) BigDecimal amount) {}

    public record EvidenceRequest(
            @NotNull UUID fileId,
            @Size(max = 300) String caption,
            @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
            @NotNull LocalDate capturedOn) {}

    public record Expenditure(UUID id, LocalDate spentOn, String description, BigDecimal amount, String currency, BigDecimal amountBase,
                              String baseCurrency, BigDecimal fxRate, LocalDate fxRateDate, Long version) {}

    public record Evidence(UUID id, UUID fileId, String fileName, String contentType, String caption, BigDecimal latitude,
                           BigDecimal longitude, LocalDate capturedOn, BigDecimal distanceKm, boolean farFromSite) {}

    public record Project(UUID id, UUID orgId, String orgName, String name, String category, String description, String community,
                          String stateRegion, String lga, BigDecimal latitude, BigDecimal longitude, BigDecimal budget, String currency,
                          BigDecimal spent, BigDecimal spentBase, String baseCurrency, BigDecimal budgetUsedPct, Integer plannedBeneficiaries,
                          Integer actualBeneficiaries, String status, LocalDate startDate, LocalDate endDate, List<Integer> sdgs,
                          boolean piaHostCommunity, int evidenceCount, int openGrievances, List<Expenditure> expenditures,
                          List<Evidence> evidence, Long version) {}

    // ---------------------------------------------------------------- grievances

    public record GrievanceRequest(
            @NotNull UUID orgId,
            UUID projectId,
            @Size(max = 255) String community,
            @NotNull Boolean anonymous,
            @Size(max = 200) String complainantName,
            @Size(max = 200) String complainantContact,
            @NotBlank @Pattern(regexp = CHANNEL_REGEX, message = "must be a channel") String channel,
            @NotBlank @Pattern(regexp = GRIEVANCE_CATEGORY_REGEX, message = "must be a grievance category") String category,
            @NotBlank @Pattern(regexp = SEVERITY_REGEX, message = "must be low, medium, high or critical") String severity,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4000) String description,
            @NotNull LocalDate receivedOn,
            UUID assignedTo,
            Long version) {}

    public record GrievanceUpdate(String action, String fromStatus, String toStatus, Integer escalationLevel, String note, String byName,
                                  Instant at) {}

    public record GrievanceSummary(UUID id, String reference, UUID orgId, String orgName, String title, String category, String severity,
                                   String status, LocalDate receivedOn, LocalDate dueOn, boolean overdue, int escalationLevel,
                                   String assignedToName, String projectName) {}

    public record Grievance(UUID id, String reference, UUID orgId, String orgName, UUID projectId, String projectName, String community,
                            boolean anonymous, String complainantName, String complainantContact, String channel, String category,
                            String severity, String title, String description, LocalDate receivedOn, String status, UUID assignedTo,
                            String assignedToName, Instant acknowledgedAt, LocalDate dueOn, boolean overdue, int escalationLevel,
                            Instant escalatedAt, String resolution, LocalDate resolvedOn, String resolvedByName, String complainantSatisfied,
                            Instant closedAt, List<GrievanceUpdate> updates, List<String> actions, Map<String, String> blockedActions,
                            Long version) {}

    public record EscalationRun(int tenants, int escalated) {}

    // ---------------------------------------------------------------- overview

    public record WorkforceOverview(LocalDate asOf, int snapshots, WorkforceMetrics.Result metrics, BigDecimal hoursWorked) {}

    public record CommunityOverview(int activeProjects, int completedProjects, BigDecimal investmentBase, String baseCurrency,
                                    long beneficiaries, int piaHostCommunityProjects) {}

    public record GrievanceOverview(int open, int overdue, int escalated, int receivedInYear, int resolvedInYear,
                                    BigDecimal averageDaysToResolve, Map<String, Integer> openByCategory) {}

    public record Overview(int year, WorkforceOverview workforce, SafetyStatistics safety, CommunityOverview community,
                           GrievanceOverview grievances) {}

    public record Person(UUID id, String name) {}
}
