package com.esg.compliance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * GapAnalysisEngine (TRD §8.3, §6.5.4; BRD-5.2.3-04; design D23). Pure: the caller loads requirements, metric links,
 * the year's data and attestations; this decides each requirement's status, completeness, priority and next step.
 *
 * <p>Per leaf requirement (headings with children are aggregated, not counted):
 * <ol>
 *   <li>attested not applicable → {@code not_applicable};</li>
 *   <li>quantitative and linked to metrics → {@code met} when every metric has approved data for the year,
 *       {@code partial} when some do or any is still in the workflow (completeness = approved ÷ linked metrics),
 *       else {@code missing}. Linked metrics can be alternatives (e.g. fuels for Scope 1): with at least one approved,
 *       an attestation of complete (with evidence) makes it {@code met};</li>
 *   <li>narrative → attested complete (with evidence) = {@code met}, attested in progress = {@code partial} (50%);
 *       supporting metrics with approved data but no attestation = {@code partial} (50%);</li>
 *   <li>otherwise an <em>equivalent</em> mapped requirement in another framework that is met carries over as
 *       {@code met} ("enter once, map to many"); a met <em>partial</em> mapping gives {@code partial} (50%);</li>
 *   <li>else {@code missing}.</li>
 * </ol>
 * Priority score (0–100) for unmet requirements = framework weight (mandatory 40, recommended 20, voluntary 10)
 * + 30 missing / 15 partial + 20 if quantitative + 10 if the framework has a filing due within 90 days;
 * high ≥ 70, medium ≥ 45, else low.
 */
public final class GapAnalysisEngine {

    public enum Status { met, partial, missing, not_applicable }

    public record Req(UUID id, String code, String title, String category, String dataType, UUID parentId) {}

    /** Approved = at least one approved value for the year; pending = data in the workflow (draft…validated). */
    public record MetricState(boolean approved, boolean pending) {}

    public record Attestation(String status) {}

    /** A mapping to another framework's requirement, seen from this requirement's side. */
    public record Link(String framework, String code, String relation, boolean met) {}

    public record Input(String frameworkType, boolean deadlineWithin90Days, List<Req> requirements,
                        Map<UUID, List<String>> metrics, Map<String, MetricState> metricStates,
                        Map<UUID, Attestation> attestations, Map<UUID, List<Link>> links, Map<String, String> metricNames) {}

    public record Result(UUID id, String code, String title, String category, UUID parentId, boolean leaf, boolean quantitative, Status status,
                         String basis, BigDecimal completeness, List<String> metricKeys, List<String> missingMetrics,
                         String satisfiedBy, Integer priorityScore, String priority, String recommendation) {}

    public record Summary(int total, int met, int partial, int missing, int notApplicable, BigDecimal completeness,
                          int highPriority, List<Result> requirements) {}

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal HALF = BigDecimal.valueOf(50);

    private GapAnalysisEngine() {}

    public static Summary analyse(Input in) {
        Set<UUID> parents = new HashSet<>();
        in.requirements().forEach(r -> {
            if (r.parentId() != null) {
                parents.add(r.parentId());
            }
        });
        Map<UUID, Result> results = new LinkedHashMap<>();
        for (Req r : in.requirements()) {
            if (!parents.contains(r.id())) {
                results.put(r.id(), leaf(in, r));
            }
        }
        // Headings: aggregate their children (deepest first, so nested headings see their children's results).
        List<Req> headings = new ArrayList<>(in.requirements().stream().filter(r -> parents.contains(r.id())).toList());
        Map<UUID, Integer> depth = new HashMap<>();
        Map<UUID, Req> byId = new HashMap<>();
        in.requirements().forEach(r -> byId.put(r.id(), r));
        headings.forEach(h -> depth.put(h.id(), depthOf(h, byId)));
        headings.sort((a, b) -> depth.get(b.id()) - depth.get(a.id()));
        for (Req h : headings) {
            List<Result> children = in.requirements().stream().filter(c -> h.id().equals(c.parentId()))
                    .map(c -> results.get(c.id())).filter(java.util.Objects::nonNull).toList();
            results.put(h.id(), heading(h, children));
        }
        // Back in catalogue order.
        List<Result> ordered = in.requirements().stream().map(r -> results.get(r.id())).toList();
        List<Result> leaves = ordered.stream().filter(Result::leaf).toList();
        int met = count(leaves, Status.met);
        int partial = count(leaves, Status.partial);
        int missing = count(leaves, Status.missing);
        int na = count(leaves, Status.not_applicable);
        List<Result> applicable = leaves.stream().filter(r -> r.status() != Status.not_applicable).toList();
        BigDecimal completeness = applicable.isEmpty() ? (leaves.isEmpty() ? BigDecimal.ZERO : HUNDRED)
                : applicable.stream().map(Result::completeness).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(applicable.size()), 1, RoundingMode.HALF_UP);
        int high = (int) leaves.stream().filter(r -> "high".equals(r.priority())).count();
        return new Summary(leaves.size(), met, partial, missing, na, completeness.setScale(1, RoundingMode.HALF_UP), high, ordered);
    }

    private static Result leaf(Input in, Req r) {
        List<String> metrics = in.metrics().getOrDefault(r.id(), List.of());
        Attestation att = in.attestations().get(r.id());
        String attested = att == null ? "not_started" : att.status();
        boolean quantitative = isQuantitative(r, metrics);

        if ("not_applicable".equals(attested)) {
            return result(r, Status.not_applicable, "attested", HUNDRED, metrics, List.of(), null, null, in);
        }
        if (quantitative) {
            List<String> notApproved = metrics.stream()
                    .filter(m -> !in.metricStates().getOrDefault(m, new MetricState(false, false)).approved()).toList();
            boolean anyPending = metrics.stream().anyMatch(m -> in.metricStates().getOrDefault(m, new MetricState(false, false)).pending());
            if (notApproved.isEmpty()) {
                return result(r, Status.met, "data", HUNDRED, metrics, List.of(), null, null, in);
            }
            if ("complete".equals(attested) && notApproved.size() < metrics.size()) {
                return result(r, Status.met, "attested", HUNDRED, metrics, List.of(), null, null, in);
            }
            BigDecimal pct = BigDecimal.valueOf(metrics.size() - notApproved.size()).multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(metrics.size()), 1, RoundingMode.HALF_UP);
            Status s = notApproved.size() < metrics.size() || anyPending ? Status.partial : Status.missing;
            return result(r, s, "data", pct, metrics, notApproved, null, quantitative, in);
        }
        if ("complete".equals(attested)) {
            return result(r, Status.met, "attested", HUNDRED, metrics, List.of(), null, null, in);
        }
        List<Link> links = in.links().getOrDefault(r.id(), List.of());
        Link equivalent = links.stream().filter(l -> l.met() && "equivalent".equals(l.relation())).findFirst().orElse(null);
        if (equivalent != null && !"in_progress".equals(attested)) {
            return result(r, Status.met, "mapped", HUNDRED, metrics, List.of(), equivalent.framework() + " " + equivalent.code(), null, in);
        }
        if ("in_progress".equals(attested)) {
            return result(r, Status.partial, "attested", HALF, metrics, List.of(), null, false, in);
        }
        if (metrics.stream().anyMatch(m -> in.metricStates().getOrDefault(m, new MetricState(false, false)).approved())) {
            return result(r, Status.partial, "supporting_data", HALF, metrics, List.of(), null, false, in);
        }
        Link partialLink = links.stream().filter(l -> l.met() && "partial".equals(l.relation())).findFirst().orElse(null);
        if (partialLink != null) {
            return result(r, Status.partial, "mapped", HALF, metrics, List.of(), partialLink.framework() + " " + partialLink.code(), false, in);
        }
        return result(r, Status.missing, "none", BigDecimal.ZERO, metrics, List.of(), null, false, in);
    }

    private static Result result(Req r, Status s, String basis, BigDecimal pct, List<String> metrics, List<String> missingMetrics,
                                 String satisfiedBy, Boolean quantitative, Input in) {
        Integer score = null;
        String priority = null;
        String recommendation = null;
        if (s == Status.partial || s == Status.missing) {
            score = priorityScore(in.frameworkType(), s, Boolean.TRUE.equals(quantitative), in.deadlineWithin90Days());
            priority = priority(score);
            recommendation = recommendation(s, basis, missingMetrics, satisfiedBy, in.metricNames());
        }
        return new Result(r.id(), r.code(), r.title(), r.category(), r.parentId(), true, isQuantitative(r, metrics), s, basis,
                pct.setScale(1, RoundingMode.HALF_UP),
                metrics, missingMetrics, satisfiedBy, score, priority, recommendation);
    }

    private static Result heading(Req h, List<Result> children) {
        List<Result> applicable = children.stream().filter(c -> c.status() != Status.not_applicable).toList();
        Status s;
        if (children.isEmpty() || applicable.isEmpty()) {
            s = children.isEmpty() ? Status.missing : Status.not_applicable;
        } else if (applicable.stream().allMatch(c -> c.status() == Status.met)) {
            s = Status.met;
        } else if (applicable.stream().allMatch(c -> c.status() == Status.missing)) {
            s = Status.missing;
        } else {
            s = Status.partial;
        }
        BigDecimal pct = applicable.isEmpty() ? (children.isEmpty() ? BigDecimal.ZERO : HUNDRED)
                : applicable.stream().map(Result::completeness).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(applicable.size()), 1, RoundingMode.HALF_UP);
        return new Result(h.id(), h.code(), h.title(), h.category(), h.parentId(), false, false, s, "children", pct, List.of(), List.of(),
                null, null, null, null);
    }

    static int priorityScore(String frameworkType, Status status, boolean quantitative, boolean deadlineSoon) {
        int weight = switch (frameworkType == null ? "" : frameworkType) {
            case "mandatory" -> 40;
            case "recommended" -> 20;
            default -> 10;
        };
        return weight + (status == Status.missing ? 30 : 15) + (quantitative ? 20 : 0) + (deadlineSoon ? 10 : 0);
    }

    static String priority(int score) {
        return score >= 70 ? "high" : score >= 45 ? "medium" : "low";
    }

    private static String recommendation(Status s, String basis, List<String> missingMetrics, String satisfiedBy,
                                         Map<String, String> names) {
        if ("data".equals(basis)) {
            String list = String.join(", ", missingMetrics.stream().map(m -> names.getOrDefault(m, m)).toList());
            return s == Status.missing
                    ? "Enter " + list + " for the year in the Data Hub and have it approved"
                    : "Get " + list + " approved in the Data Hub";
        }
        if ("mapped".equals(basis)) {
            return "Partly covered by " + satisfiedBy + ": check what this requirement asks for beyond it, then attest";
        }
        if ("attested".equals(basis)) {
            return "Finish the disclosure and attest it complete with evidence";
        }
        if ("supporting_data".equals(basis)) {
            return "The supporting data is approved: write the disclosure and attest it complete with evidence";
        }
        return "Prepare the disclosure and attest it with evidence, or mark it not applicable with the reason";
    }

    /** Met by data alone: a quantitative requirement with linked metrics (narrative ones need an attestation). */
    public static boolean isQuantitative(Req r, List<String> metrics) {
        return "quantitative".equals(r.dataType()) && !metrics.isEmpty();
    }

    private static int count(List<Result> rs, Status s) {
        return (int) rs.stream().filter(r -> r.status() == s).count();
    }

    private static int depthOf(Req r, Map<UUID, Req> byId) {
        int d = 0;
        for (UUID p = r.parentId(); p != null && byId.containsKey(p) && d < 20; p = byId.get(p).parentId()) {
            d++;
        }
        return d;
    }
}
