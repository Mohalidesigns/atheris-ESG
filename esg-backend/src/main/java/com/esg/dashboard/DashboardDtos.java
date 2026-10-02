package com.esg.dashboard;

import com.esg.carbon.EmissionService.TrendPoint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Executive dashboard (WP-24, D29). A section is null when the caller can't read the module behind it. */
public final class DashboardDtos {
    private DashboardDtos() {}

    public record Emissions(String granularity, List<TrendPoint> trend, BigDecimal currentYear, BigDecimal previousYear,
                            BigDecimal changePct) {}

    public record FrameworkScore(String code, String name, boolean active, String contentStatus, int requirements, int met,
                                 int partial, int missing, BigDecimal completeness) {}

    public record Compliance(int year, BigDecimal averageActive, List<FrameworkScore> frameworks) {}

    /** One SDG: catalogue metrics linked to it, how many have approved data this year, and which. */
    public record Sdg(int goal, String title, int linkedMetrics, int evidencedMetrics, BigDecimal coveragePct, List<String> evidenced,
                      List<String> missing) {}

    public record SdgAlignment(int year, String basis, List<Sdg> goals) {}

    public record Activity(Instant at, String user, String verb, String kind, String title, UUID id, String link) {}

    public record Filing(UUID id, String framework, String description, LocalDate deadlineDate, long daysLeft, String status,
                         String owner) {}

    /** WP-34: board and policy health; open whistleblower cases only for whistleblower:read_summary / read (counts, no content). */
    public record Governance(int directors, BigDecimal independentPct, BigDecimal femalePct, int codeChecksPassed, int codeChecksFailed,
                             int codeChecksInsufficient, int policiesPublished, int policiesDueForReview, Integer openCases) {}

    /** WP-33: workforce from approved figures, safety rates for the year, grievances and community investment (user's organisations). */
    public record Social(Integer headcount, BigDecimal womenPct, BigDecimal womenInManagementPct, BigDecimal ltifr, BigDecimal trir,
                         long lostTimeInjuries, BigDecimal hoursWorked, int openGrievances, int overdueGrievances,
                         BigDecimal communityInvestment, String baseCurrency, int activeProjects) {}

    public record Executive(int year, Emissions emissions, Compliance compliance, SdgAlignment sdg, List<Activity> activity,
                            List<Filing> filings, Governance governance, Social social, com.esg.risk.RiskDtos.Overview risk) {}
}
