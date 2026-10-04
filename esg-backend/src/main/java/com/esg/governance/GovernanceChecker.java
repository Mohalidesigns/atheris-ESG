package com.esg.governance;

import com.esg.governance.GovernanceDtos.BoardMember;
import com.esg.governance.GovernanceDtos.RuleResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GovernanceComplianceChecker (TRD §8.8): evaluates the typed rules of reference.governance_rules against the active board
 * register. Pure: each rule says pass, fail or insufficient_data (the register lacks what the rule needs), with evidence.
 */
public final class GovernanceChecker {

    private GovernanceChecker() {}

    public record Rule(String code, String ruleSet, String provision, String title, String ruleType, JsonNode params, String appliesTo,
                       String source, String verificationStatus) {}

    public static RuleResult check(Rule r, List<BoardMember> active) {
        List<BoardMember> directors = active.stream().filter(m -> !"company_secretary".equals(m.boardRole())).toList();
        Outcome o = switch (r.ruleType()) {
            case "min_board_size" -> {
                int min = r.params().path("min").asInt();
                yield new Outcome(directors.size() >= min, directors.size() + " directors (minimum " + min + ")");
            }
            case "min_independent" -> {
                int min = r.params().path("min").asInt();
                long n = directors.stream().filter(GovernanceChecker::independent).count();
                yield new Outcome(n >= min, n + " independent directors (minimum " + min + ")");
            }
            case "majority_non_executive" -> {
                if (directors.isEmpty()) {
                    yield Outcome.unknown("No directors in the register");
                }
                long ned = directors.stream().filter(m -> !m.executive()).count();
                yield new Outcome(ned * 2 > directors.size(), ned + " of " + directors.size() + " directors are non-executive");
            }
            case "chair_not_ceo" -> {
                List<BoardMember> chairs = directors.stream().filter(m -> "chair".equals(m.boardRole())).toList();
                if (chairs.isEmpty()) {
                    yield Outcome.unknown("No chair in the register");
                }
                boolean separateCeo = directors.stream().anyMatch(m -> "md_ceo".equals(m.boardRole()));
                boolean executiveChair = chairs.stream().anyMatch(BoardMember::executive);
                yield executiveChair && !separateCeo
                        ? new Outcome(false, chairs.get(0).name() + " is an executive chair and no separate MD/CEO is recorded")
                        : separateCeo ? new Outcome(true, "Chair " + chairs.get(0).name() + "; MD/CEO is a different director")
                        : Outcome.unknown("No MD/CEO recorded to compare with the chair");
            }
            case "chair_non_executive" -> {
                List<BoardMember> chairs = directors.stream().filter(m -> "chair".equals(m.boardRole())).toList();
                if (chairs.isEmpty()) {
                    yield Outcome.unknown("No chair in the register");
                }
                BoardMember chair = chairs.get(0);
                boolean needIndependent = r.params().path("independent").asBoolean(false);
                boolean ok = !chair.executive() && (!needIndependent || chair.independent());
                yield new Outcome(ok, chair.name() + " is " + (chair.executive() ? "an executive" : "a non-executive")
                        + (needIndependent ? (chair.independent() ? ", independent" : ", not independent") : "") + " chair");
            }
            case "max_ined_tenure_years" -> {
                int years = r.params().path("years").asInt();
                List<BoardMember> ineds = directors.stream().filter(GovernanceChecker::independent).toList();
                if (ineds.isEmpty()) {
                    yield Outcome.unknown("No independent directors in the register");
                }
                List<String> over = ineds.stream().filter(m -> m.tenureYears() != null && m.tenureYears().compareTo(BigDecimal.valueOf(years)) > 0)
                        .map(m -> m.name() + " (" + m.tenureYears() + " years)").toList();
                yield over.isEmpty() ? new Outcome(true, "All " + ineds.size() + " independent directors within " + years + " years")
                        : new Outcome(false, "Over " + years + " years: " + String.join(", ", over));
            }
            case "company_secretary" -> new Outcome(active.stream().anyMatch(m -> "company_secretary".equals(m.boardRole())),
                    active.stream().anyMatch(m -> "company_secretary".equals(m.boardRole())) ? "Company secretary recorded" : "No company secretary recorded");
            case "committee_exists" -> {
                String committee = r.params().path("committee").asText();
                List<String> members = directors.stream().filter(m -> m.committees().contains(committee)).map(BoardMember::name).toList();
                yield new Outcome(!members.isEmpty(), members.isEmpty() ? "No director sits on the " + committee.replace('_', ' ') + " committee"
                        : members.size() + " members: " + members.stream().collect(Collectors.joining(", ")));
            }
            case "min_gender_share" -> {
                if (directors.isEmpty()) {
                    yield Outcome.unknown("No directors in the register");
                }
                String gender = r.params().path("gender").asText("female");
                double pct = r.params().path("pct").asDouble();
                long n = directors.stream().filter(m -> gender.equals(m.gender())).count();
                double share = 100.0 * n / directors.size();
                yield new Outcome(share >= pct, String.format("%.1f%% %s (minimum %.0f%%)", share, gender, pct));
            }
            default -> Outcome.unknown("Unknown rule type " + r.ruleType());
        };
        return new RuleResult(r.code(), r.ruleSet(), r.provision(), r.title(), r.appliesTo(), o.result(), o.evidence(), r.source(),
                r.verificationStatus());
    }

    static boolean independent(BoardMember m) {
        return m.independent() || "independent_non_executive_director".equals(m.boardRole());
    }

    private record Outcome(String result, String evidence) {
        Outcome(boolean pass, String evidence) {
            this(pass ? "pass" : "fail", evidence);
        }

        static Outcome unknown(String evidence) {
            return new Outcome("insufficient_data", evidence);
        }
    }
}
