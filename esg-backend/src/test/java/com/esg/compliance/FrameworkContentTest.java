package com.esg.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Phase 3 (5): the content file format, checked before anything is written. */
class FrameworkContentTest {

  private static final Set<String> METRICS = Set.of("scope1_tco2e", "electricity_kwh");
  private static final String HEADER = "requirement_code,title,category,data_type,parent_code,source_ref,metric_keys,description\n";

  private static FrameworkContent.Parsed csv(String body) {
    return FrameworkContent.parse((HEADER + body).getBytes(StandardCharsets.UTF_8), "csv", METRICS);
  }

  @Test
  void aValidCsvParsesWithParentsMetricsAndQuotedCommas() {
    FrameworkContent.Parsed p = csv("S1,Section,GEN,narrative,,s.1,,\n"
        + "1.1,\"Emissions, Scope 1\",e,Quantitative,S1,s.1.1,scope1_tco2e; electricity_kwh ,note\n");
    assertThat(p.ok()).isTrue();
    assertThat(p.rows()).hasSize(2);
    FrameworkContent.Row r = p.rows().get(1);
    assertThat(r.title()).isEqualTo("Emissions, Scope 1");
    assertThat(r.category()).isEqualTo("E");
    assertThat(r.dataType()).isEqualTo("quantitative");
    assertThat(r.parentCode()).isEqualTo("S1");
    assertThat(r.metricKeys()).containsExactly("scope1_tco2e", "electricity_kwh");
    assertThat(p.checksum()).hasSize(64);
  }

  @Test
  void everyRowProblemIsReportedWithItsLine() {
    FrameworkContent.Parsed p = csv("A,One,E,narrative,,,,\nA,Two,E,narrative,,,,\nB,,X,number,C,,nope,\nC,Late parent,G,narrative,,,,\n");
    assertThat(p.ok()).isFalse();
    assertThat(p.errors()).extracting(FrameworkContent.RowError::line).contains(3, 4);
    assertThat(p.errors().toString()).contains("appears twice", "title", "must be E, S, G or GEN", "quantitative or narrative",
        "listed above", "isn't in the metric catalogue");
  }

  @Test
  void theHeaderMustMatchAndTheFileMustHaveRows() {
    assertThat(FrameworkContent.parse("code,title\nA,B\n".getBytes(StandardCharsets.UTF_8), "csv", METRICS).errors().get(0).message())
        .contains("header must be exactly");
    assertThat(csv("").errors().get(0).message()).contains("no requirements");
    assertThat(csv("A,One,E,narrative\n").errors().get(0).message()).contains("expected 8");
  }

  @Test
  void jsonUsesTheSameFieldsInCamelCase() {
    String json = "{\"requirements\":[{\"requirementCode\":\"A\",\"title\":\"T\",\"category\":\"S\",\"dataType\":\"narrative\","
        + "\"metricKeys\":[\"scope1_tco2e\"]}]}";
    FrameworkContent.Parsed p = FrameworkContent.parse(json.getBytes(StandardCharsets.UTF_8), "json", METRICS);
    assertThat(p.ok()).isTrue();
    assertThat(p.rows().get(0).metricKeys()).containsExactly("scope1_tco2e");
    assertThat(FrameworkContent.parse("[]".getBytes(StandardCharsets.UTF_8), "json", METRICS).errors().get(0).message())
        .contains("requirements");
  }
}
