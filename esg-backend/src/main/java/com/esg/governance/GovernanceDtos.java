package com.esg.governance;

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
import java.util.Map;
import java.util.UUID;

/** M8 Governance & Ethics API bodies (WP-34, D30). The frontend Zod schemas (features/governance/schemas.js) mirror these. */
public final class GovernanceDtos {
    private GovernanceDtos() {}

    public static final String BOARD_ROLE_REGEX = "^(chair|vice_chair|md_ceo|executive_director|non_executive_director|"
            + "independent_non_executive_director|company_secretary)$";
    public static final String COMMITTEE_REGEX = "^(audit|risk|nomination_governance|remuneration|sustainability|finance|technical)$";
    public static final String POLICY_CATEGORY_REGEX = "^(code_of_conduct|anti_bribery_corruption|whistleblowing|related_party|"
            + "conflict_of_interest|sustainability|health_safety|human_rights|data_protection|board_charter|other)$";
    public static final String CASE_CATEGORY_REGEX = "^(bribery_corruption|fraud|harassment_discrimination|health_safety|environment|"
            + "human_rights|conflict_of_interest|data_privacy|other)$";
    public static final String CASE_STATUS_REGEX = "^(received|triage|investigating|resolved|closed|dismissed)$";

    // ---------------------------------------------------------------- board

    public record BoardMemberRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Pattern(regexp = BOARD_ROLE_REGEX, message = "must be a board role") String boardRole,
            @NotNull Boolean independent,
            @NotNull Boolean executive,
            @Pattern(regexp = "^(female|male|undisclosed)$", message = "must be female, male or undisclosed") String gender,
            @Pattern(regexp = "^(under_40|40_49|50_59|60_plus)$", message = "must be an age band") String ageBand,
            @NotNull LocalDate appointedOn,
            LocalDate leftOn,
            @Size(max = 7) List<@Pattern(regexp = COMMITTEE_REGEX, message = "must be a committee") String> committees,
            @Size(max = 15) List<@NotBlank @Size(max = 60) String> skills,
            @Size(max = 1000) String notes,
            Long version) {}

    public record BoardMember(UUID id, String name, String boardRole, boolean independent, boolean executive, String gender,
                              String ageBand, LocalDate appointedOn, LocalDate leftOn, BigDecimal tenureYears, boolean active,
                              List<String> committees, List<String> skills, String notes, Long version) {}

    /** Board diversity and independence as at a date (TRD §8.8 BoardCompositionTracker). Company secretaries aren't directors. */
    public record Composition(LocalDate asOf, int directors, int executives, int nonExecutives, int independent,
                              BigDecimal independentPct, int female, BigDecimal femalePct, BigDecimal averageTenureYears,
                              Map<String, Integer> byAgeBand, Map<String, Integer> committees, Map<String, Integer> skills,
                              boolean companySecretary) {}

    /** One governance code check: pass, fail or not enough data in the register to decide. */
    public record RuleResult(String code, String ruleSet, String provision, String title, String appliesTo, String result,
                             String evidence, String source, String verificationStatus) {}

    public record CheckerReport(LocalDate asOf, int passed, int failed, int insufficientData, List<RuleResult> results) {}

    // ---------------------------------------------------------------- policies

    public record PolicyRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Pattern(regexp = POLICY_CATEGORY_REGEX, message = "must be a policy category") String category,
            @NotBlank @Size(max = 20) @Pattern(regexp = "^[0-9A-Za-z.\\-]+$", message = "letters, digits, dots and hyphens") String policyVersion,
            @Size(max = 1000) String summary,
            @Size(max = 100000) String content,
            @NotNull UUID ownerUserId,
            UUID fileId,
            @NotNull @Min(1) @Max(60) Integer reviewMonths,
            LocalDate effectiveDate,
            @NotNull Boolean requiresAcknowledgement,
            Long version) {}

    public record PolicyTransition(@NotNull Long version, @Size(max = 1000) String reason) {}

    public record Policy(UUID id, String name, String category, String policyVersion, String status, String summary, String content,
                         UUID ownerUserId, String ownerName, UUID fileId, int reviewMonths, LocalDate effectiveDate,
                         LocalDate lastReviewed, LocalDate nextReview, boolean reviewDue, boolean requiresAcknowledgement,
                         int acknowledged, int staff, BigDecimal acknowledgedPct, String submittedBy, Instant submittedAt,
                         String approvedBy, Instant approvedAt, Instant publishedAt, String statusReason,
                         List<String> actions, Map<String, String> blockedActions, Long version) {}

    public record MyPolicy(UUID id, String name, String category, String policyVersion, String summary, UUID fileId,
                           Instant publishedAt, Instant acknowledgedAt) {}

    // ---------------------------------------------------------------- whistleblower

    public record Submission(
            @NotBlank @Pattern(regexp = CASE_CATEGORY_REGEX, message = "choose a category") String category,
            @NotBlank @Size(min = 20, max = 10000, message = "between 20 and 10000 characters") String description) {}

    /** Shown once to the reporter: the reference and the secret are all they need to follow the case. */
    public record Receipt(String caseReference, String secret) {}

    public record StatusRequest(@NotBlank @Size(max = 30) String caseReference, @NotBlank @Size(max = 40) String secret) {}

    public record CaseStatus(String caseReference, String status, String publicUpdate, Instant updatedAt) {}

    public record CaseSummary(UUID id, String caseReference, String category, String status, UUID assignedTo, String assignedName,
                              Instant receivedAt, Instant updatedAt) {}

    public record CaseNote(String author, Instant at, String text) {}

    public record CaseDetail(UUID id, String caseReference, String category, String status, String description, UUID assignedTo,
                             String assignedName, String publicUpdate, String resolution, Instant receivedAt, Instant updatedAt,
                             List<CaseNote> notes, Long version) {}

    public record CaseUpdate(
            @NotNull Long version,
            @NotBlank @Pattern(regexp = CASE_STATUS_REGEX, message = "must be a case status") String status,
            UUID assignedTo,
            @Size(max = 1000) String publicUpdate,
            @Size(max = 10000) String resolution) {}

    public record NoteRequest(@NotBlank @Size(max = 10000) String note) {}

    /** Anonymised counts for board members (whistleblower:read_summary): no content, no references. */
    public record CaseStatistics(LocalDate since, int total, int open, Map<String, Integer> byStatus, Map<String, Integer> byCategory) {}
}
