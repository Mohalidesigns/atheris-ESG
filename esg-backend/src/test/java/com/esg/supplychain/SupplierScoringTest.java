package com.esg.supplychain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Golden values for the WP-31 scorecard (computed by hand; D33). */
class SupplierScoringTest {

  private static final List<SupplierScoring.Question> QUESTIONS = List.of(
      new SupplierScoring.Question("e1", "E", "yes_no", "yes", 3, false),   // energy measured
      new SupplierScoring.Question("e2", "E", "scale", null, 1, false),     // waste management 1–5
      new SupplierScoring.Question("e3", "E", "number", null, 0, false),    // litres of diesel: informational
      new SupplierScoring.Question("s1", "S", "yes_no", "no", 2, false),    // child labour found? positive = no
      new SupplierScoring.Question("s2", "S", "yes_no", "yes", 2, true),    // H&S policy, evidence required
      new SupplierScoring.Question("g1", "G", "scale", null, 1, false));    // anti-bribery maturity

  private static SupplierScoring.Answer a(String choice) {
    return new SupplierScoring.Answer(choice, false);
  }

  @Test
  void weightedPillarsAndTotal() {
    // E: (3 × 100 + 1 × 75) ÷ 4 = 93.75 → 93.8; S: (2 × 100 + 2 × 100) ÷ 4 = 100; G: 50.
    // Total with weights 40/30/30: (93.75→93.8 × 40 + 100 × 30 + 50 × 30) ÷ 100 = 82.5 (from the rounded pillar scores).
    var r = SupplierScoring.score(QUESTIONS, Map.of("e1", a("yes"), "e2", a("4"), "s1", a("no"), "s2", new SupplierScoring.Answer("yes", true),
        "g1", a("3")), 40, 30, 30);
    assertThat(r.e()).isEqualByComparingTo("93.8");
    assertThat(r.s()).isEqualByComparingTo("100.0");
    assertThat(r.g()).isEqualByComparingTo("50.0");
    assertThat(r.total()).isEqualByComparingTo("82.5");
    assertThat(r.riskLevel()).isEqualTo("low");
    assertThat(r.gaps()).isEmpty();
  }

  @Test
  void missingEvidenceUnansweredAndWrongAnswersScoreZero() {
    // E: e1 unanswered → 0, e2 = 5 → 100: (0 + 100) ÷ 4 = 25.0; S: s1 'yes' (wrong) 0, s2 no evidence 0 → 0.0; G: 1 → 0.0.
    var r = SupplierScoring.score(QUESTIONS, Map.of("e2", a("5"), "s1", a("yes"), "s2", a("yes"), "g1", a("1")), 40, 30, 30);
    assertThat(r.e()).isEqualByComparingTo("25.0");
    assertThat(r.s()).isEqualByComparingTo("0.0");
    assertThat(r.g()).isEqualByComparingTo("0.0");
    assertThat(r.total()).isEqualByComparingTo("10.0");
    assertThat(r.riskLevel()).isEqualTo("high");
    assertThat(r.gaps()).extracting(SupplierScoring.Gap::reason)
        .contains("not answered", "evidence required but not attached", "answer doesn't meet the expectation", "rated 1 of 5");
  }

  @Test
  void pillarsWithoutScoredQuestionsDropOutOfTheTotal() {
    // Only E is scored: total = E. 55 → medium.
    var r = SupplierScoring.score(List.of(new SupplierScoring.Question("x", "E", "scale", null, 2, false),
        new SupplierScoring.Question("t", "S", "text", null, 0, false)), Map.of("x", a("3")), 20, 40, 40);
    assertThat(r.e()).isEqualByComparingTo("50.0");
    assertThat(r.s()).isNull();
    assertThat(r.total()).isEqualByComparingTo("50.0");
    assertThat(r.riskLevel()).isEqualTo("medium");
    assertThat(SupplierScoring.tier(new java.math.BigDecimal("70"))).isEqualTo("low");
    assertThat(SupplierScoring.tier(new java.math.BigDecimal("39.9"))).isEqualTo("high");
    assertThat(SupplierScoring.score(List.of(), Map.of(), 40, 30, 30).total()).isNull();
  }
}
