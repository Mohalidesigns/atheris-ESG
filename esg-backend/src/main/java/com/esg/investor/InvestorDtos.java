package com.esg.investor;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M10 Investor Portal API bodies (WP-40, D35). The frontend Zod schemas (features/investor/schemas.js) mirror these. */
public final class InvestorDtos {
    private InvestorDtos() {}

    public static final String INSTRUMENT_REGEX =
            "^(green_bond|social_bond|sustainability_bond|sustainability_linked_bond|green_sukuk|sustainability_sukuk)$";
    public static final String CURRENCY_REGEX = "^(NGN|USD|EUR|GBP|KES|ZAR|GHS|XOF|EGP)$";
    public static final String CATEGORY_REGEX = "^(renewable_energy|energy_efficiency|pollution_prevention|sustainable_land_use|biodiversity|"
            + "clean_transport|sustainable_water|climate_adaptation|circular_economy|green_buildings|affordable_infrastructure|essential_services|"
            + "affordable_housing|employment_generation|food_security|socioeconomic_advancement)$";
    public static final String PURPOSE_REGEX = "^(due_diligence|investor_relations|dfi_monitoring|bond_reporting|other)$";

    public record ActionRequest(@NotBlank @Size(max = 30) String action, @Size(max = 1000) String note, @NotNull Long version) {}

    public record Person(UUID id, String name, String email) {}

    // ---------------------------------------------------------------- scorecards

    public record SettingsRequest(@NotNull @Min(0) @Max(100) Integer weightE, @NotNull @Min(0) @Max(100) Integer weightS,
                                  @NotNull @Min(0) @Max(100) Integer weightG, Long version) {}

    public record Settings(int weightE, int weightS, int weightG, long version) {}

    public record GenerateRequest(@NotNull @Min(2000) @Max(2100) Integer year, @NotBlank @Size(max = 200) String title,
                                  @Size(max = 4000) String commentary) {}

    public record Pillar(String pillar, int weight, BigDecimal score, List<ScorecardMath.Component> components) {}

    public record Scorecard(UUID id, int year, String title, String commentary, BigDecimal total, String grade, List<Pillar> pillars, String status,
                            String generatedByName, Instant generatedAt, String publishedByName, Instant publishedAt, String withdrawReason,
                            Instant withdrawnAt, List<String> actions, Map<String, String> blockedActions, long version) {}

    public record Preview(int year, BigDecimal total, String grade, List<Pillar> pillars) {}

    // ---------------------------------------------------------------- shared reports

    public record ShareRequest(@NotNull UUID reportId, @Size(max = 1000) String note) {}

    public record SharedReport(UUID id, UUID reportId, String title, String versionLabel, LocalDate periodStart, LocalDate periodEnd,
                               String frameworkCode, String note, String sharedByName, Instant sharedAt, Instant revokedAt, String fileName) {}

    public record DfiRequest(@NotBlank @Pattern(regexp = "^(dfi_ifc_ps|dfi_afdb_iss)$", message = "must be an available DFI template") String template,
                             @NotNull @Min(2000) @Max(2100) Integer year, @Size(max = 500) String title) {}

    public record DfiTemplate(String key, UUID templateId, String name, String description, String source, String contentStatus) {}

    // ---------------------------------------------------------------- data rooms

    public record RoomRequest(@NotBlank @Size(max = 200) String name,
                              @NotBlank @Pattern(regexp = PURPOSE_REGEX, message = "must be a data room purpose") String purpose,
                              @Size(max = 2000) String description,
                              @NotNull LocalDate expiresOn,
                              @NotBlank @Pattern(regexp = "^(open|closed)$", message = "must be open or closed") String status,
                              @NotNull Boolean watermark,
                              @NotNull @Size(max = 200) List<@NotNull UUID> memberIds,
                              Long version) {}

    public record RoomDocument(UUID id, UUID fileId, String title, String fileName, String contentType, Long sizeBytes, Instant addedAt) {}

    public record Room(UUID id, String name, String purpose, String description, LocalDate expiresOn, String status, boolean watermark, boolean expired,
                       List<UUID> memberIds, List<Person> members, List<RoomDocument> documents, long version) {}

    public record DocumentRequest(@NotNull UUID fileId, @NotBlank @Size(max = 200) String title) {}

    public record AccessEntry(Instant at, String userName, String email, String action, String documentTitle, boolean watermarked) {}

    // ---------------------------------------------------------------- bonds

    public record BondRequest(
            @NotBlank @Pattern(regexp = INSTRUMENT_REGEX, message = "must be a sustainable finance instrument") String instrument,
            @NotBlank @Size(max = 200) String name,
            @Pattern(regexp = "^([A-Z]{2}[A-Z0-9]{9}[0-9])?$", message = "must be a 12-character ISIN") String isin,
            @NotNull LocalDate issueDate,
            @NotNull LocalDate maturityDate,
            @NotNull @Positive @Digits(integer = 18, fraction = 2) BigDecimal amount,
            @NotBlank @Pattern(regexp = CURRENCY_REGEX, message = "must be a supported currency") String currency,
            @NotBlank @Size(max = 300) String frameworkReference,
            @Size(max = 200) String externalReviewer,
            @Size(max = 200) String shariahReviewer,
            LocalDate shariahPronouncementOn,
            UUID kpiId,
            @Digits(integer = 14, fraction = 6) BigDecimal sptValue,
            @Min(2000) @Max(2100) Integer sptObservationYear,
            @Min(0) @Max(1000) Integer couponStepUpBps,
            @NotNull Boolean visibleToInvestors,
            @NotBlank @Pattern(regexp = "^(active|matured|redeemed)$", message = "must be active, matured or redeemed") String status,
            Long version) {}

    public record AllocationRequest(
            @NotNull UUID orgId,
            @NotBlank @Pattern(regexp = CATEGORY_REGEX, message = "must be an eligible category") String category,
            @NotBlank @Size(max = 200) String project,
            @NotNull @Positive @Digits(integer = 18, fraction = 2) BigDecimal amount,
            @NotNull LocalDate allocatedOn,
            @Size(max = 1000) String impactNote,
            Long version) {}

    public record Allocation(UUID id, UUID orgId, String orgName, String category, String project, BigDecimal amount, LocalDate allocatedOn,
                             String impactNote, long version) {}

    public record SptStatus(String kpiName, String unit, String direction, BigDecimal target, int observationYear, BigDecimal actual, String status,
                            String note) {}

    public record Bond(UUID id, String instrument, String name, String isin, LocalDate issueDate, LocalDate maturityDate, BigDecimal amount,
                       String currency, String frameworkReference, String externalReviewer, String shariahReviewer, LocalDate shariahPronouncementOn,
                       UUID kpiId, BigDecimal sptValue, Integer sptObservationYear, Integer couponStepUpBps, boolean visibleToInvestors, String status,
                       BigDecimal allocated, BigDecimal unallocated, BigDecimal allocatedPct, Map<String, BigDecimal> byCategory, SptStatus spt,
                       List<Allocation> allocations, long version) {}

    // ---------------------------------------------------------------- impact metrics

    public record ImpactRequest(
            @NotBlank @Pattern(regexp = "^[A-Z]{2}[0-9]{4}$", message = "must be an IRIS+ code like PI4060") String irisCode,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 100) String metricKey,
            UUID kpiId,
            @Size(max = 1000) String note,
            @NotNull Boolean visibleToInvestors,
            Long version) {}

    public record Impact(UUID id, String irisCode, String name, String metricKey, UUID kpiId, String sourceName, String unit, String note,
                         boolean visibleToInvestors, BigDecimal current, BigDecimal previous, int year, long version) {}

    // ---------------------------------------------------------------- dashboard

    public record Dashboard(String company, boolean manager, Scorecard latest, List<Scorecard> history, List<SharedReport> reports, List<Room> rooms,
                            List<Bond> bonds, List<Impact> impact) {}
}
