package com.esg.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.esg.reporting.ReportingDtos.VariableValue;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Variable syntax, rendering from a snapshot and number formatting (D24 (2)). */
class VariableEngineTest {

  @Test
  void findsEachVariableOnceInOrder() {
    assertThat(VariableEngine.variables(List.of("A {{metric:electricity_kwh}} B {{ carbon:scope1 }}", "{{metric:electricity_kwh}} {{compliance:GRI:met}}",
        "no variables", "{{bad}} {{Metric:x}}")))
        .containsExactly("metric:electricity_kwh", "carbon:scope1", "compliance:GRI:met");
  }

  @Test
  void rendersSnapshotValuesAndMarksMissingOnes() {
    Map<String, VariableValue> snap = Map.of(
        "metric:electricity_kwh", new VariableValue("1,750 kWh", new BigDecimal("1750"), "kWh", "ok", "3 approved data points"),
        "metric:water_withdrawal_m3", new VariableValue("[not reported]", null, null, "missing", "no approved data in the period"));
    assertThat(VariableEngine.render("Electricity: {{metric:electricity_kwh}}; water: {{metric:water_withdrawal_m3}}; scope 3: {{carbon:scope3}}", snap))
        .isEqualTo("Electricity: 1,750 kWh; water: [not reported]; scope 3: [not reported]");
    assertThat(VariableEngine.render("Costs $5 {{metric:electricity_kwh}}", snap)).isEqualTo("Costs $5 1,750 kWh");
    assertThat(VariableEngine.render(null, snap)).isNull();
  }

  @Test
  void formatsNumbersWithThousandsAndUpToThreeDecimals() {
    assertThat(VariableEngine.num(new BigDecimal("1234567.5"))).isEqualTo("1,234,567.5");
    assertThat(VariableEngine.num(new BigDecimal("2.680"))).isEqualTo("2.68");
    assertThat(VariableEngine.num(new BigDecimal("0.75250"))).isEqualTo("0.752");
    assertThat(VariableEngine.num(new BigDecimal("130"))).isEqualTo("130");
  }

  @Test
  void exportFileNamesAreSafe() {
    assertThat(ExportService.fileName("Sustainability report 2039 / draft?", "v1.0", "pdf")).isEqualTo("Sustainability-report-2039-draft-v1.0.pdf");
    assertThat(ExportService.fileName("***", "v2.0", "xlsx")).isEqualTo("report-v2.0.xlsx");
  }
}
