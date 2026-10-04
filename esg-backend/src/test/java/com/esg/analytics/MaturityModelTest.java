package com.esg.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Golden values for the WP-35 maturity index (computed by hand; D34 (6)). */
class MaturityModelTest {

  private static Map<String, Integer> scores(MaturityModel.Result r) {
    return r.dimensions().stream().collect(Collectors.toMap(MaturityModel.Dimension::key, MaturityModel.Dimension::score));
  }

  @Test
  void aTypicalYearScoresCompliance() {
    var r = MaturityModel.score(new MaturityModel.Inputs(33, 12,                // 36 % of the catalogue → 3
        true, true, false, true,                                                 // Scope 1 + 2, no Scope 3 → 3
        45.0,                                                                    // 40–60 % complete → 3
        true, 0.25, true, false,                                                 // board, policy (a quarter independent) → 3
        true, true, false, true,                                                 // materiality, risks, KPI target → 4
        false, true, true, false));                                              // workforce, safety → 3
    assertThat(scores(r)).containsExactlyInAnyOrderEntriesOf(Map.of("data", 3, "carbon", 3, "compliance", 3, "governance", 3, "risk", 4,
        "value_chain", 3));
    // (3 + 3 + 3 + 3 + 4 + 3) ÷ 6 = 3.17 → 3.2, level 3.
    assertThat(r.overall()).isEqualByComparingTo("3.2");
    assertThat(r.level()).isEqualTo(3);
    assertThat(r.levelName()).isEqualTo("Compliance");
    var data = r.dimensions().get(0);
    assertThat(data.evidence()).containsExactly("12 of 33 catalogue metrics have approved data (36%)");
    assertThat(data.nextStep()).isEqualTo("Approve data for 20 of the 33 catalogue metrics (Data Hub)");   // ⌈60 % × 33⌉
    assertThat(r.dimensions().get(1).nextStep()).startsWith("Add Scope 3");
    assertThat(r.dimensions().get(3).nextStep()).startsWith("Bring independent directors");
  }

  @Test
  void nothingRecordedIsInitial() {
    var r = MaturityModel.score(new MaturityModel.Inputs(33, 0, false, false, false, false, null, false, null, false, false,
        false, false, false, false, false, false, false, false));
    assertThat(r.overall()).isEqualByComparingTo("1.0");
    assertThat(r.level()).isEqualTo(1);
    assertThat(r.levelName()).isEqualTo("Initial");
    assertThat(r.dimensions().get(2).nextStep()).isEqualTo("Choose the frameworks you report against (Compliance)");
  }

  @Test
  void everythingInPlaceIsTransformative() {
    var r = MaturityModel.score(new MaturityModel.Inputs(33, 30, true, true, true, true, 85.0, true, 1.0 / 3, true, true,
        true, true, true, true, true, true, true, true));
    assertThat(scores(r).values()).containsOnly(5);
    assertThat(r.overall()).isEqualByComparingTo("5.0");
    assertThat(r.levelName()).isEqualTo("Transformative");
    assertThat(r.dimensions()).allSatisfy(d -> assertThat(d.nextStep()).isNull());
  }
}
