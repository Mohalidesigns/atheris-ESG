package com.esg.supplychain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
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

/** M6 Supply Chain ESG API bodies (WP-31, D33). The frontend Zod schemas (features/supplyChain/schemas.js) mirror these. */
public final class SupplyDtos {
    private SupplyDtos() {}

    public static final String TIER_REGEX = "^(tier_1|tier_2|tier_3)$";
    public static final String SUPPLIER_STATUS_REGEX = "^(pending|active|suspended|offboarded)$";
    public static final String ONBOARDING_REGEX = "^(full|simplified)$";
    public static final String COUNTRY_REGEX = "^[A-Z]{2}$";
    public static final String CURRENCY_REGEX = "^(NGN|USD|EUR|GBP|KES|ZAR|GHS|XOF|EGP)$";
    public static final String PILLAR_REGEX = "^(E|S|G)$";
    public static final String ANSWER_TYPE_REGEX = "^(yes_no|scale|number|text)$";
    public static final String SEVERITY_REGEX = "^(low|medium|high)$";

    public record Person(UUID id, String name) {}

    public record ActionRequest(@NotBlank @Size(max = 30) String action, @Size(max = 2000) String note, @NotNull Long version) {}

    // ---------------------------------------------------------------- suppliers

    public record SupplierRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Pattern(regexp = TIER_REGEX, message = "must be tier 1, 2 or 3") String tier,
            @NotBlank @Pattern(regexp = ONBOARDING_REGEX, message = "must be full or simplified") String onboarding,
            @Size(max = 10) String sectorCode,
            @NotBlank @Pattern(regexp = COUNTRY_REGEX, message = "must be a 2-letter country code") String countryCode,
            @Size(max = 100) String stateRegion,
            @Size(max = 50) String registrationNumber,
            @Size(max = 200) String contactName,
            @Email(message = "must be an email address") @Size(max = 255) String contactEmail,
            @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "must be a phone number") String contactPhone,
            @Size(max = 2000) String description,
            @NotBlank @Pattern(regexp = SUPPLIER_STATUS_REGEX, message = "must be pending, active, suspended or offboarded") String status,
            UUID ownerUserId,
            Long version) {}

    public record ScoreHistory(UUID questionnaireId, String title, LocalDate reviewedOn, BigDecimal score, BigDecimal e, BigDecimal s, BigDecimal g) {}

    public record Supplier(UUID id, String name, String tier, String onboarding, String sectorCode, String sectorName, String countryCode,
                           String stateRegion, String registrationNumber, String contactName, String contactEmail, String contactPhone,
                           String description, String status, UUID ownerUserId, String ownerName, BigDecimal esgScore, String riskLevel,
                           Instant lastAssessedAt, boolean local, BigDecimal spendBase, int openActions, int openQuestionnaires,
                           List<ScoreHistory> history, Long version) {}

    // ---------------------------------------------------------------- templates

    public record QuestionRequest(
            @NotBlank @Pattern(regexp = PILLAR_REGEX, message = "must be E, S or G") String pillar,
            @NotBlank @Size(max = 500) String question,
            @Size(max = 1000) String helpText,
            @NotBlank @Pattern(regexp = ANSWER_TYPE_REGEX, message = "must be yes/no, scale, number or text") String answerType,
            @Pattern(regexp = "^(yes|no)$", message = "must be yes or no") String positiveAnswer,
            @NotNull @Min(0) @Max(10) Integer weight,
            @NotNull Boolean evidenceRequired) {}

    public record TemplateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = ONBOARDING_REGEX, message = "must be full or simplified") String kind,
            @Size(max = 2000) String description,
            @NotNull @Min(0) @Max(100) Integer weightE,
            @NotNull @Min(0) @Max(100) Integer weightS,
            @NotNull @Min(0) @Max(100) Integer weightG,
            @NotNull @Size(min = 1, max = 60) List<@Valid QuestionRequest> questions,
            Long version) {}

    public record Question(UUID id, String pillar, String question, String helpText, String answerType, String positiveAnswer, int weight,
                           boolean evidenceRequired, int sortOrder) {}

    public record Template(UUID id, String name, String kind, String description, int weightE, int weightS, int weightG, String status,
                           List<Question> questions, int sent, Long version) {}

    // ---------------------------------------------------------------- questionnaires

    public record SendRequest(@NotNull UUID supplierId, @NotNull UUID templateId, @NotNull LocalDate dueDate, @Size(max = 255) String title) {}

    public record Sent(UUID questionnaireId, String link, String emailStatus) {}

    public record AnswerView(UUID questionId, String choice, BigDecimal number, String text, String comment, UUID fileId, String fileName) {}

    public record Questionnaire(UUID id, UUID supplierId, String supplierName, UUID templateId, String templateName, String title, String status,
                                LocalDate dueDate, boolean overdue, Instant sentAt, Instant submittedAt, Instant reviewedAt, String reviewedByName,
                                String reviewNote, BigDecimal score, BigDecimal scoreE, BigDecimal scoreS, BigDecimal scoreG, String riskLevel,
                                List<Question> questions, List<AnswerView> answers, List<SupplierScoring.Gap> gaps, List<String> actions,
                                Long version) {}

    /** What the supplier sees through the link: their own questionnaire, nothing about the buyer's other suppliers. */
    public record PublicQuestionnaire(String buyer, String supplierName, String title, String status, LocalDate dueDate, List<Question> questions,
                                      List<AnswerView> answers, String reviewNote, String maxUpload) {}

    public record AnswerRequest(
            @NotNull UUID questionId,
            @Pattern(regexp = "^(yes|no|[1-5])$", message = "must be yes, no or 1–5") String choice,
            @Digits(integer = 16, fraction = 4) BigDecimal number,
            @Size(max = 4000) String text,
            @Size(max = 2000) String comment,
            UUID fileId) {}

    public record AnswersRequest(@NotNull @Size(max = 60) List<@Valid AnswerRequest> answers) {}

    // ---------------------------------------------------------------- corrective actions

    public record CorrectiveActionRequest(
            @NotNull UUID supplierId,
            UUID questionnaireId,
            UUID questionId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            @NotBlank @Pattern(regexp = SEVERITY_REGEX, message = "must be low, medium or high") String severity,
            UUID ownerUserId,
            @NotNull LocalDate dueDate,
            Long version) {}

    public record CorrectiveAction(UUID id, UUID supplierId, String supplierName, UUID questionnaireId, UUID questionId, String questionText,
                                   String title, String description, String severity, UUID ownerUserId, String ownerName, LocalDate dueDate,
                                   boolean overdue, String status, String implementationNote, String implementedByName, Instant implementedAt,
                                   String verificationNote, String verifiedByName, Instant verifiedAt, Instant closedAt, List<String> actions,
                                   Map<String, String> blockedActions, Long version) {}

    public record ReminderRun(int tenants, int reminders) {}

    // ---------------------------------------------------------------- spend

    public record SpendRequest(
            @NotNull UUID supplierId,
            @NotNull UUID orgId,
            @NotNull LocalDate reportingPeriodStart,
            @NotNull LocalDate reportingPeriodEnd,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 2) BigDecimal amount,
            @NotBlank @Pattern(regexp = CURRENCY_REGEX, message = "must be one of NGN, USD, EUR, GBP, KES, ZAR, GHS, XOF, EGP") String currency,
            @NotBlank @Size(max = 100) String sourceActivity,
            @Size(max = 500) String description,
            Long version) {}

    public record Spend(UUID id, UUID supplierId, String supplierName, UUID orgId, String orgName, LocalDate reportingPeriodStart,
                        LocalDate reportingPeriodEnd, BigDecimal amount, String currency, String sourceActivity, String activityLabel,
                        String description, UUID emissionRecordId, BigDecimal co2eTonnes, Long version) {}

    public record ImportResult(int rows, int created, List<Map<String, Object>> errors) {}

    // ---------------------------------------------------------------- overview

    public record Overview(int suppliers, int active, int assessed, Map<String, Integer> byRisk, BigDecimal averageScore, int openActions,
                           int overdueActions, int awaitingReview, BigDecimal spendBase, BigDecimal localSpendPct, String baseCurrency,
                           BigDecimal scope3Cat1Tonnes, Map<String, BigDecimal> sectorAverages, int year) {}
}
