package com.esg.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ESG maturity index (TRD §8.9 ESGMaturityScorer; D34 (6)): six dimensions scored 1–5 from what the platform holds for a
 * year, the overall score their mean to one decimal, the level its whole part. Thresholds are Atheris defaults pending
 * the ESG SME. Golden values: MaturityModelTest.
 */
public final class MaturityModel {
    private MaturityModel() {}

    // no-mock-gate: config — maturity level names (D34 (6))
    public static final Map<Integer, String> LEVELS = Map.of(1, "Initial", 2, "Awareness", 3, "Compliance", 4, "Strategic", 5, "Transformative");

    /** What the platform holds for the year. complianceCompleteness is null when no active framework has content. */
    public record Inputs(int catalogueMetrics, int metricsWithApprovedData,
                         boolean scope1, boolean scope2, boolean scope3, boolean reductionTarget,
                         Double complianceCompleteness,
                         boolean boardRecorded, Double independentShare, boolean policyPublished, boolean whistleblowingPolicy,
                         boolean materialityApproved, boolean risksAssessed, boolean scenarioAnalysis, boolean kpiWithTarget,
                         boolean supplierAssessed, boolean workforceApproved, boolean safetyRecorded, boolean communityInvestment) {}

    public record Dimension(String key, String name, int score, List<String> evidence, String nextStep) {}

    public record Result(BigDecimal overall, int level, String levelName, List<Dimension> dimensions) {}

    public static Result score(Inputs in) {
        List<Dimension> d = List.of(data(in), carbon(in), compliance(in), checklist("governance", "Governance", new Check[] {
                new Check(in.boardRecorded(), "Board register recorded", "Record the board in Governance → Board"),
                new Check(in.independentShare() != null && in.independentShare() >= 1.0 / 3, "At least a third of the board independent",
                        "Bring independent directors to at least a third of the board"),
                new Check(in.policyPublished(), "A governance policy published", "Publish a policy in Governance → Policies"),
                new Check(in.whistleblowingPolicy(), "Whistleblowing policy published", "Publish a whistleblowing policy")}),
                checklist("risk", "Risk & strategy", new Check[] {
                        new Check(in.materialityApproved(), "Double materiality assessment approved", "Approve a materiality assessment (Risk → Materiality)"),
                        new Check(in.risksAssessed(), "ESG risks assessed in the register", "Assess ESG risks in Risk → Risk register"),
                        new Check(in.scenarioAnalysis(), "Climate scenario analysis done", "Run climate scenario analysis (Risk → Climate scenarios)"),
                        new Check(in.kpiWithTarget(), "A KPI with a target tracked", "Set a target on a custom KPI (Analytics → KPIs)")}),
                checklist("value_chain", "Value chain & social", new Check[] {
                        new Check(in.supplierAssessed(), "Suppliers assessed", "Review a supplier questionnaire (Supply Chain)"),
                        new Check(in.workforceApproved(), "Workforce figures approved", "Approve workforce figures (Social → Workforce)"),
                        new Check(in.safetyRecorded(), "Health and safety recorded", "Record hours worked and incidents (Social → Health & safety)"),
                        new Check(in.communityInvestment(), "Community investment recorded", "Record community project spend (Social → Community)")}));
        int sum = d.stream().mapToInt(Dimension::score).sum();
        BigDecimal overall = BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(d.size()), 1, RoundingMode.HALF_UP);
        int level = Math.max(1, Math.min(5, overall.intValue()));
        return new Result(overall, level, LEVELS.get(level), d);
    }

    private record Check(boolean met, String evidence, String next) {}

    private static Dimension checklist(String key, String name, Check[] checks) {
        List<String> evidence = new ArrayList<>();
        String next = null;
        for (Check c : checks) {
            if (c.met()) {
                evidence.add(c.evidence());
            } else if (next == null) {
                next = c.next();
            }
        }
        return new Dimension(key, name, 1 + evidence.size(), evidence, next);
    }

    private static Dimension data(Inputs in) {
        double pct = in.catalogueMetrics() == 0 ? 0 : 100.0 * in.metricsWithApprovedData() / in.catalogueMetrics();
        int score = pct >= 85 ? 5 : pct >= 60 ? 4 : pct >= 30 ? 3 : pct >= 10 ? 2 : 1;
        int[] steps = {10, 30, 60, 85};
        String next = score == 5 ? null : "Approve data for " + (int) Math.ceil(steps[score - 1] / 100.0 * in.catalogueMetrics())
                + " of the " + in.catalogueMetrics() + " catalogue metrics (Data Hub)";
        return new Dimension("data", "Data coverage", score, List.of(in.metricsWithApprovedData() + " of " + in.catalogueMetrics()
                + " catalogue metrics have approved data (" + Math.round(pct) + "%)"), next);
    }

    private static Dimension carbon(Inputs in) {
        List<String> evidence = new ArrayList<>();
        if (in.scope1()) {
            evidence.add("Scope 1 recorded");
        }
        if (in.scope2()) {
            evidence.add("Scope 2 recorded");
        }
        if (in.scope3()) {
            evidence.add("Scope 3 recorded");
        }
        if (in.reductionTarget()) {
            evidence.add("Reduction target set");
        }
        int score;
        String next;
        if (!in.scope1() && !in.scope2()) {
            score = 1;
            next = "Record Scope 1 or Scope 2 emissions (Carbon)";
        } else if (!(in.scope1() && in.scope2())) {
            score = 2;
            next = "Record both Scope 1 and Scope 2";
        } else if (!in.scope3()) {
            score = 3;
            next = "Add Scope 3 (for example from supplier spend)";
        } else if (!in.reductionTarget()) {
            score = 4;
            next = "Set a reduction target (Carbon → Reduction targets)";
        } else {
            score = 5;
            next = null;
        }
        return new Dimension("carbon", "Carbon accounting", score, evidence, next);
    }

    private static Dimension compliance(Inputs in) {
        Double c = in.complianceCompleteness();
        if (c == null) {
            return new Dimension("compliance", "Compliance", 1, List.of(), "Choose the frameworks you report against (Compliance)");
        }
        int score = c >= 80 ? 5 : c >= 60 ? 4 : c >= 40 ? 3 : c >= 20 ? 2 : 1;
        int[] steps = {20, 40, 60, 80};
        return new Dimension("compliance", "Compliance", score, List.of("Active frameworks " + BigDecimal.valueOf(c).setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "% complete"), score == 5 ? null : "Close gaps to reach " + steps[score - 1] + "% (Compliance → Gap analysis)");
    }
}
