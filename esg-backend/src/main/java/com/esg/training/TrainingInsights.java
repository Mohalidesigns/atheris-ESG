package com.esg.training;

import com.esg.training.TrainingDtos.Overview;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** What training gives other modules (D37 (6)): report variables and the dashboard card. */
@Component
public class TrainingInsights {

    // no-mock-gate: config — training report variables (WP-42)
    public static final Set<String> VARIABLES = Set.of("hours_total", "hours_per_employee", "completion_rate", "certificates_issued");

    public record Value(String text, BigDecimal number, String unit, String basis) {}

    private final JdbcTemplate jdbc;

    public TrainingInsights(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code training:<key>} for the year. */
    public Optional<Value> variable(String key, int year) {
        Overview o = overview(year);
        String basis = "training completed in " + year + " (Training)";
        return switch (key) {
            case "hours_total" -> Optional.of(new Value(o.hours().stripTrailingZeros().toPlainString() + " hours", o.hours(), "hours", basis));
            case "hours_per_employee" -> Optional.ofNullable(o.hoursPerEmployee()).map(v -> new Value(v.toPlainString() + " hours", v, "hours",
                    basis + " ÷ people with an active account"));
            case "completion_rate" -> Optional.ofNullable(o.completionRate()).map(v -> new Value(v.toPlainString() + "%", v, "%",
                    "enrolments due or started in " + year + " that were passed"));
            case "certificates_issued" -> Optional.of(new Value(String.valueOf(o.certificatesIssued()), BigDecimal.valueOf(o.certificatesIssued()), null,
                    "certificates issued in " + year));
            default -> Optional.empty();
        };
    }

    public Overview overview(int year) {
        Date start = Date.valueOf(LocalDate.of(year, 1, 1));
        Date end = Date.valueOf(LocalDate.of(year, 12, 31));
        Map<String, Object> r = jdbc.queryForMap("select (select count(*) from training.courses where deleted_at is null and status = 'published') as published, "
                + "(select count(*) from training.enrollments where status in ('enrolled', 'in_progress', 'failed')) as enrolled, "
                + "(select count(*) from training.enrollments where status <> 'withdrawn' and (completed_on between ? and ? or "
                + "(completed_on is null and created_at::date <= ?))) as assigned, "
                + "(select count(*) from training.enrollments where status = 'passed' and completed_on between ? and ?) as passed, "
                + "(select coalesce(sum(hours), 0) from training.enrollments where status = 'passed' and completed_on between ? and ?) as hours, "
                + "(select count(*) from training.certificates where issued_on between ? and ?) as certificates, "
                + "(select count(*) from auth.users u where u.is_active and u.deleted_at is null and not auth.is_platform_user(u.id)) as people",
                start, end, end, start, end, start, end, start, end);
        BigDecimal hours = new BigDecimal(r.get("hours").toString());
        long people = ((Number) r.get("people")).longValue();
        long assigned = ((Number) r.get("assigned")).longValue();
        return new Overview(year, ((Number) r.get("published")).intValue(), ((Number) r.get("enrolled")).intValue(),
                TrainingMath.completionRate(((Number) r.get("passed")).longValue(), assigned), ((Number) r.get("certificates")).intValue(),
                hours.setScale(1, RoundingMode.HALF_UP), TrainingMath.hoursPerEmployee(hours, people));
    }
}
