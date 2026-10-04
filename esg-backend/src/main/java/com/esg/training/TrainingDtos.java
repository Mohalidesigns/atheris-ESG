package com.esg.training;

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
import java.util.List;
import java.util.UUID;

/** M12 Training API bodies (WP-42, D37). The frontend Zod schemas (features/training/schemas.js) mirror these. */
public final class TrainingDtos {
    private TrainingDtos() {}

    public static final String CATEGORY_REGEX = "^(esg_fundamentals|climate_carbon|reporting_disclosure|social_human_rights|governance_ethics|"
            + "data_collection|regulatory|other)$";

    public record ActionRequest(@NotBlank @Size(max = 30) String action, @Size(max = 1000) String note, @NotNull Long version) {}

    public record Person(UUID id, String name, String email, List<String> roles) {}

    // ---------------------------------------------------------------- courses

    public record ModuleRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Pattern(regexp = "^(text|pdf|video)$", message = "must be text, pdf or video") String kind,
            @Size(max = 20000) String body,
            UUID fileId,
            @Size(max = 500) @Pattern(regexp = "^(https://.+)?$", message = "must start with https://") String url,
            @NotNull @Min(1) @Max(600) Integer minutes) {}

    public record QuestionRequest(
            @NotBlank @Size(max = 1000) String question,
            @NotNull @Size(min = 2, max = 6, message = "give 2 to 6 options") List<@NotBlank @Size(max = 300) String> options,
            @NotNull @Min(0) Integer correctIndex) {}

    public record CourseRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Z0-9][A-Z0-9-]*$", message = "capital letters, digits and hyphens") String code,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @NotBlank @Pattern(regexp = CATEGORY_REGEX, message = "must be a course category") String category,
            @NotBlank @Pattern(regexp = "^(online|classroom)$", message = "must be online or classroom") String delivery,
            @NotNull @DecimalMin(value = "0.1") @DecimalMax("500") @Digits(integer = 3, fraction = 1) BigDecimal durationHours,
            @NotNull @Min(0) @Max(100) Integer passingScore,
            @Min(1) @Max(120) Integer certificateValidityMonths,
            @NotNull @Size(max = 10) List<@NotNull UUID> prerequisiteIds,
            @NotNull @Size(max = 50) List<@Valid @NotNull ModuleRequest> modules,
            @NotNull @Size(max = 50) List<@Valid @NotNull QuestionRequest> questions,
            Long version) {}

    public record Module(UUID id, String title, String kind, String body, UUID fileId, String fileName, String url, int minutes) {}

    /** correctIndex is shown only to people who manage courses. */
    public record Question(UUID id, String question, List<String> options, Integer correctIndex) {}

    public record Course(UUID id, String code, String title, String description, String category, String delivery, BigDecimal durationHours,
                         int passingScore, Integer certificateValidityMonths, List<UUID> prerequisiteIds, List<String> prerequisiteTitles, String status,
                         Instant publishedAt, List<Module> modules, List<Question> questions, int enrolled, int passed, List<String> actions, long version) {}

    // ---------------------------------------------------------------- learning paths

    public record PathRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @Size(max = 100) String targetRole,
            @NotNull @Size(min = 1, max = 50, message = "add at least one course") List<@NotNull UUID> courseIds,
            @NotBlank @Pattern(regexp = "^(active|archived)$", message = "must be active or archived") String status,
            Long version) {}

    public record Path(UUID id, String name, String description, String targetRole, List<UUID> courseIds, List<String> courseTitles, String status,
                       long version) {}

    // ---------------------------------------------------------------- enrolments

    public record AssignRequest(@NotNull UUID courseId, @NotNull @Size(min = 1, max = 500) List<@NotNull UUID> userIds, LocalDate dueOn) {}

    public record AssignPathRequest(@NotNull UUID pathId, @Size(max = 500) List<UUID> userIds, LocalDate dueOn) {}

    public record AssignResult(int enrolled, int alreadyEnrolled) {}

    public record QuizRequest(@NotNull @Size(max = 50) List<Integer> answers, @NotNull Long version) {}

    public record AttendanceRequest(@NotNull LocalDate completedOn, @NotNull @DecimalMin(value = "0.1") @DecimalMax("500") @Digits(integer = 3, fraction = 1)
                                    BigDecimal hours, @NotNull Boolean passed, Integer score, @NotNull Long version) {}

    public record Enrollment(UUID id, UUID courseId, String courseCode, String courseTitle, String delivery, BigDecimal durationHours, UUID userId,
                             String userName, String source, LocalDate dueOn, boolean overdue, String status, List<UUID> modulesDone, int modulesTotal,
                             int attempts, Integer bestScore, LocalDate completedOn, BigDecimal hours, Certificate certificate, long version) {}

    public record Certificate(UUID id, String code, String holderName, String courseTitle, Integer score, LocalDate issuedOn, LocalDate expiresOn,
                              String status, String revokeReason, String verifyUrl) {}

    public record Verification(String code, String status, String holderName, String courseTitle, String organisation, LocalDate issuedOn,
                               LocalDate expiresOn) {}

    public record Gap(UUID courseId, String courseTitle, String pathName, String state) {}

    public record Progress(List<Enrollment> enrollments, List<Gap> gaps, BigDecimal hoursThisYear) {}

    public record TeamRow(UUID userId, String name, List<String> roles, int required, int passed, int overdue, BigDecimal hoursThisYear, List<Gap> gaps) {}

    public record Overview(int year, int published, int enrolled, BigDecimal completionRate, int certificatesIssued, BigDecimal hours,
                           BigDecimal hoursPerEmployee) {}
}
