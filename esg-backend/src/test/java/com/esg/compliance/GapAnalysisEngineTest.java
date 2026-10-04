package com.esg.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import com.esg.compliance.GapAnalysisEngine.Attestation;
import com.esg.compliance.GapAnalysisEngine.Input;
import com.esg.compliance.GapAnalysisEngine.Link;
import com.esg.compliance.GapAnalysisEngine.MetricState;
import com.esg.compliance.GapAnalysisEngine.Req;
import com.esg.compliance.GapAnalysisEngine.Result;
import com.esg.compliance.GapAnalysisEngine.Status;
import com.esg.compliance.GapAnalysisEngine.Summary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Golden tests for the gap rules, priority scoring and reminder intervals (D23 (1), (4)). */
class GapAnalysisEngineTest {

  private static final UUID HEAD = UUID.randomUUID();
  private static final UUID Q1 = UUID.randomUUID(); // quantitative, 2 metrics
  private static final UUID Q2 = UUID.randomUUID(); // quantitative, 1 metric
  private static final UUID N1 = UUID.randomUUID(); // narrative
  private static final UUID N2 = UUID.randomUUID(); // narrative with supporting metric
  private static final UUID N3 = UUID.randomUUID(); // narrative, mapped

  private static List<Req> reqs() {
    return List.of(new Req(HEAD, "H", "Heading", "E", null, null), new Req(Q1, "Q1", "Water", "E", "quantitative", HEAD),
        new Req(Q2, "Q2", "Waste", "E", "quantitative", HEAD), new Req(N1, "N1", "Policy", "G", "narrative", null),
        new Req(N2, "N2", "Workforce", "S", "narrative", null), new Req(N3, "N3", "Oversight", "G", "narrative", null));
  }

  private static Map<UUID, List<String>> metrics() {
    return Map.of(Q1, List.of("water_in", "water_out"), Q2, List.of("waste"), N2, List.of("employees"));
  }

  private static Summary run(String type, boolean soon, Map<String, MetricState> states, Map<UUID, Attestation> att,
                             Map<UUID, List<Link>> links) {
    return GapAnalysisEngine.analyse(new Input(type, soon, reqs(), metrics(), states, att, links, Map.of("water_in", "Water withdrawn",
        "water_out", "Water discharged", "waste", "Waste generated")));
  }

  private static Result find(Summary s, UUID id) {
    return s.requirements().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
  }

  @Test
  void nothingRecordedEverythingMissingWithPriorities() {
    Summary s = run("mandatory", false, Map.of(), Map.of(), Map.of());
    // Leaves only: the heading isn't counted.
    assertThat(s.total()).isEqualTo(5);
    assertThat(s.missing()).isEqualTo(5);
    assertThat(s.completeness()).isEqualByComparingTo("0.0");
    // mandatory 40 + missing 30 + quantitative 20 = 90 (high); narrative = 70 (high).
    assertThat(find(s, Q1).priorityScore()).isEqualTo(90);
    assertThat(find(s, Q1).priority()).isEqualTo("high");
    assertThat(find(s, N1).priorityScore()).isEqualTo(70);
    assertThat(find(s, Q1).recommendation()).isEqualTo("Enter Water withdrawn, Water discharged for the year in the Data Hub and have it approved");
    assertThat(find(s, HEAD).leaf()).isFalse();
    assertThat(find(s, HEAD).status()).isEqualTo(Status.missing);
    assertThat(s.highPriority()).isEqualTo(5);
  }

  @Test
  void dataClosesQuantitativeGapsAndHeadingsAggregate() {
    Map<String, MetricState> states = new HashMap<>();
    states.put("water_in", new MetricState(true, false));
    states.put("water_out", new MetricState(false, true)); // still in the workflow
    states.put("waste", new MetricState(true, false));
    Summary s = run("voluntary", false, states, Map.of(), Map.of());
    assertThat(find(s, Q2).status()).isEqualTo(Status.met);
    assertThat(find(s, Q2).basis()).isEqualTo("data");
    Result q1 = find(s, Q1);
    assertThat(q1.status()).isEqualTo(Status.partial);
    assertThat(q1.completeness()).isEqualByComparingTo("50.0");
    assertThat(q1.missingMetrics()).containsExactly("water_out");
    // voluntary 10 + partial 15 + quantitative 20 = 45 → medium.
    assertThat(q1.priorityScore()).isEqualTo(45);
    assertThat(q1.priority()).isEqualTo("medium");
    assertThat(q1.recommendation()).isEqualTo("Get Water discharged approved in the Data Hub");
    assertThat(find(s, HEAD).status()).isEqualTo(Status.partial);
    assertThat(find(s, HEAD).completeness()).isEqualByComparingTo("75.0");
  }

  @Test
  void quantitativeWithSomeDataCanBeAttestedCompleteButNotWithNone() {
    Map<UUID, Attestation> att = Map.of(Q1, new Attestation("complete"), Q2, new Attestation("complete"));
    Summary s = run("mandatory", false, Map.of("water_in", new MetricState(true, false)), att, Map.of());
    // One of two alternatives approved + attested complete: met (the other metric doesn't apply).
    assertThat(find(s, Q1).status()).isEqualTo(Status.met);
    assertThat(find(s, Q1).basis()).isEqualTo("attested");
    // No approved data at all: an attestation alone doesn't meet a quantitative requirement.
    assertThat(find(s, Q2).status()).isEqualTo(Status.missing);
  }

  @Test
  void pendingDataAloneIsPartialAtZeroPercent() {
    Summary s = run("mandatory", false, Map.of("waste", new MetricState(false, true)), Map.of(), Map.of());
    assertThat(find(s, Q2).status()).isEqualTo(Status.partial);
    assertThat(find(s, Q2).completeness()).isEqualByComparingTo("0.0");
  }

  @Test
  void attestationsSupportingDataAndNotApplicable() {
    Map<UUID, Attestation> att = Map.of(N1, new Attestation("complete"), Q2, new Attestation("not_applicable"),
        N3, new Attestation("in_progress"));
    Summary s = run("recommended", false, Map.of("employees", new MetricState(true, false)), att, Map.of());
    assertThat(find(s, N1).status()).isEqualTo(Status.met);
    assertThat(find(s, N1).basis()).isEqualTo("attested");
    assertThat(find(s, Q2).status()).isEqualTo(Status.not_applicable);
    // Narrative with approved supporting data but no attestation: partial, not met.
    assertThat(find(s, N2).status()).isEqualTo(Status.partial);
    assertThat(find(s, N2).basis()).isEqualTo("supporting_data");
    assertThat(find(s, N3).status()).isEqualTo(Status.partial);
    assertThat(find(s, N3).completeness()).isEqualByComparingTo("50.0");
    // Completeness over applicable leaves (N/A excluded): (0 + 100 + 50 + 50) / 4 = 50.0.
    assertThat(s.notApplicable()).isEqualTo(1);
    assertThat(s.completeness()).isEqualByComparingTo("50.0");
    // recommended 20 + missing 30 + quantitative 20 = 70 → high.
    assertThat(find(s, Q1).priorityScore()).isEqualTo(70);
  }

  @Test
  void equivalentMappingCarriesCompletionPartialMappingDoesNot() {
    Summary s = run("mandatory", false, Map.of(), Map.of(),
        Map.of(N3, List.of(new Link("TCFD", "GOV-a", "equivalent", true)), N1, List.of(new Link("GRI", "305-2", "partial", true)),
            N2, List.of(new Link("GRI", "2-7", "equivalent", false))));
    assertThat(find(s, N3).status()).isEqualTo(Status.met);
    assertThat(find(s, N3).basis()).isEqualTo("mapped");
    assertThat(find(s, N3).satisfiedBy()).isEqualTo("TCFD GOV-a");
    assertThat(find(s, N1).status()).isEqualTo(Status.partial);
    assertThat(find(s, N1).recommendation()).startsWith("Partly covered by GRI 305-2");
    assertThat(find(s, N2).status()).isEqualTo(Status.missing);
  }

  @Test
  void aDeadlineWithin90DaysRaisesPriority() {
    Summary s = run("voluntary", true, Map.of(), Map.of(), Map.of());
    // voluntary 10 + missing 30 + deadline 10 = 50 (narrative) → medium; quantitative 70 → high.
    assertThat(find(s, N1).priorityScore()).isEqualTo(50);
    assertThat(find(s, N1).priority()).isEqualTo("medium");
    assertThat(find(s, Q1).priorityScore()).isEqualTo(70);
    assertThat(find(s, Q1).priority()).isEqualTo("high");
  }

  @Test
  void reminderIntervalIsTheSmallestNotBelowTheDaysLeft() {
    List<Integer> days = List.of(90, 60, 30, 14, 7, 1);
    assertThat(DeadlineReminderService.dueInterval(days, 91)).isNull();
    assertThat(DeadlineReminderService.dueInterval(days, 90)).isEqualTo(90);
    assertThat(DeadlineReminderService.dueInterval(days, 61)).isEqualTo(90);
    assertThat(DeadlineReminderService.dueInterval(days, 60)).isEqualTo(60);
    assertThat(DeadlineReminderService.dueInterval(days, 15)).isEqualTo(30);
    assertThat(DeadlineReminderService.dueInterval(days, 1)).isEqualTo(1);
    assertThat(DeadlineReminderService.dueInterval(days, 0)).isEqualTo(1);
    assertThat(DeadlineReminderService.dueInterval(List.of(), 5)).isNull();
  }
}
