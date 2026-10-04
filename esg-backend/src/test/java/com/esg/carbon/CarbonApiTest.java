package com.esg.carbon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * WP-21 carbon accounting through the API: journeys 5–9 (grid, diesel, flaring, Scope 3 spend, targets), factor
 * provenance on every record, recalculation, market-based Scope 2, the Data Hub link and PCAF.
 * Tenant A is in Nigeria (fixture), so country factors resolve to NG.
 */
class CarbonApiTest extends ApiTestSupport {

  /** Distinct months in 2026 so records from different tests never share a period by accident. */
  private static final AtomicInteger DAY = new AtomicInteger();

  @Autowired private ObjectMapper json;

  private String director() {
    return bearerForRole("ESG_DIRECTOR");
  }

  private static String period() {
    String day = LocalDate.of(2026, 4, 1).plusDays(DAY.getAndIncrement()).toString();
    return "\"reportingPeriodStart\":\"" + day + "\",\"reportingPeriodEnd\":\"" + day + "\"";
  }

  private JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  private ResultActions postJson(String url, String body) throws Exception {
    return mvc.perform(post(url).header("Authorization", director()).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode emission(String body) throws Exception {
    return data(postJson("/v1/carbon/emissions", body).andExpect(status().isCreated()));
  }

  @Test
  void journey5GridElectricityUsesTheNigerianFactorAndKeepsItsProvenance() throws Exception {
    // The client can't supply CO2e any more: an extra co2eTonnes field is ignored.
    JsonNode r = emission("{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_2\",\"sourceActivity\":\"electricity_grid\","
        + "\"activityData\":10000,\"activityUnit\":\"kWh\",\"co2eTonnes\":999," + period() + "}");
    assertThat(r.path("co2eTonnes").decimalValue()).isEqualByComparingTo("4.3");
    assertThat(r.path("factor").path("value").decimalValue()).isEqualByComparingTo("0.43");
    assertThat(r.path("factor").path("unit").asText()).isEqualTo("kgCO2e/kWh");
    assertThat(r.path("factor").path("version").asText()).isEqualTo("1");
    assertThat(r.path("factor").path("level").asText()).isEqualTo("country");
    assertThat(r.path("factor").path("region").asText()).isEqualTo("NG");
    assertThat(r.path("factor").path("id").asText()).isNotBlank();
    assertThat(r.path("calculationMethod").asText()).isEqualTo("activity_based");
    assertThat(r.path("recordSource").asText()).isEqualTo("manual");
    // Lineage: the factor → this record.
    mvc.perform(get("/v1/audit/lineage?type=emission_record&id=" + r.path("id").asText()).header("Authorization", director()))
        .andExpect(jsonPath("$.data.upstream[0].sourceId").value(r.path("factor").path("id").asText()));
  }

  @Test
  void tenantCanBlockFactorsPendingReview() throws Exception {
    // S7: with "block", a factor pending SME review can't be used (calculator, preview); custom factors still can.
    asOwnerInTenantA("update auth.tenant_settings set provisional_factor_policy = 'block'");
    try {
      String body = "{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"heavy_fuel_oil\","
          + "\"activityData\":10,\"reportingPeriodStart\":\"2026-08-16\",\"reportingPeriodEnd\":\"2026-08-16\"}";
      postJson("/v1/carbon/emissions/preview", body)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.errorCode").value("ESG-409-FACTOR-PENDING"))
          .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("pending expert review")));
      postJson("/v1/carbon/emissions", body).andExpect(status().isConflict());
      // The refusal is audited (S3).
      mvc.perform(get("/v1/audit/logs?action=denied&resource=POST /v1/carbon/emissions").header("Authorization", director()))
          .andExpect(jsonPath("$.data.content[0].newValue.errorCode").value("ESG-409-FACTOR-PENDING"));
    } finally {
      asOwnerInTenantA("update auth.tenant_settings set provisional_factor_policy = 'allow_flagged'");
    }
  }

  // ------------------------------------------------------------------ Phase 1 (WP-52b)

  @Test
  void spendBasedScope3UsesTheEpaEeioFactors() throws Exception {
    // F4: business travel spend (category 6) has a platform factor: US EPA v1.3, NAICS 481111, 0.644 kgCO2e per USD.
    JsonNode r = emission("{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_3\",\"scope3Category\":6,\"calculationMethod\":\"spend_based\","
        + "\"sourceActivity\":\"spend_air_travel\",\"spendAmount\":1000,\"spendCurrency\":\"USD\"," + period() + "}");
    assertThat(r.path("co2eTonnes").decimalValue()).isEqualByComparingTo("0.644");
    assertThat(r.path("factor").path("source").asText()).contains("US EPA").contains("10.23719/1531143");
    assertThat(r.path("factor").path("reviewStatus").asText()).isEqualTo("pending_sme");
    // Every spend activity the platform offers has a factor.
    JsonNode activities = data(mvc.perform(get("/v1/carbon/factors/activities").header("Authorization", director())));
    JsonNode factors = data(mvc.perform(get("/v1/carbon/factors").header("Authorization", director())));
    java.util.Set<String> withFactor = new java.util.HashSet<>();
    factors.forEach(f -> withFactor.add(f.path("category").asText()));
    activities.forEach(a -> assertThat(withFactor).as(a.path("category").asText()).contains(a.path("category").asText()));
  }

  @Test
  void theActivityMustBelongToTheChosenScope() throws Exception {
    // F5: diesel is Scope 1, so it can't be calculated as Scope 2; air travel belongs to Scope 3 category 6.
    postJson("/v1/carbon/emissions/preview", "{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_2\",\"sourceActivity\":\"diesel_generator\","
        + "\"activityData\":10," + period() + "}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.sourceActivity").value("Diesel (AGO) in generators is a Scope 1 activity, not Scope 2"));
    postJson("/v1/carbon/emissions/preview", "{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_3\",\"scope3Category\":1,"
        + "\"sourceActivity\":\"air_travel_longhaul\",\"activityData\":10," + period() + "}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.sourceActivity").value(org.hamcrest.Matchers.containsString("category 6")));
    JsonNode activities = data(mvc.perform(get("/v1/carbon/factors/activities").header("Authorization", director())));
    java.util.Map<String, String> scopes = new java.util.HashMap<>();
    activities.forEach(a -> scopes.put(a.path("category").asText(), a.path("scope").asText()));
    assertThat(scopes).containsEntry("diesel_generator", "scope_1").containsEntry("electricity_grid", "scope_2")
        .containsEntry("air_travel_domestic", "scope_3").containsEntry("custom_fuel", "scope_1");
  }

  @Test
  void targetActualsIncludeTheBaseYearAndMatchTheOverview() throws Exception {
    // F6: a target whose base year has data shows that year's actual (the Overview's Scope 1 + 2), not "No data yet".
    int year = 2086; // a year no other test (e.g. the compliance gap tests) relies on being empty
    String p = "\"reportingPeriodStart\":\"" + year + "-02-01\",\"reportingPeriodEnd\":\"" + year + "-02-28\"";
    emission("{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"diesel_generator\",\"activityData\":1000," + p + "}");
    emission("{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_2\",\"sourceActivity\":\"electricity_grid\",\"activityData\":10000," + p + "}");
    JsonNode summary = data(mvc.perform(get("/v1/carbon/emissions/summary?year=" + year).header("Authorization", director())));
    java.math.BigDecimal overview = summary.path("scope1").decimalValue().add(summary.path("scope2Location").decimalValue());

    JsonNode target = data(postJson("/v1/carbon/targets", "{\"targetName\":\"Base-year target " + DAY.incrementAndGet() + "\","
        + "\"targetType\":\"absolute\",\"scope\":\"scope_1_2\",\"baseYear\":" + year + ",\"baseValue\":90,\"targetYear\":" + (year + 4)
        + ",\"targetValue\":45,\"pathway\":\"linear\"}").andExpect(status().isCreated()));
    assertThat(target.path("progressYear").asInt()).isEqualTo(year);
    assertThat(target.path("currentValue").decimalValue()).isEqualByComparingTo(overview);
    assertThat(target.path("status").asText()).isNotEqualTo("no_data");
    JsonNode basePoint = target.path("pathwayPoints").get(0);
    assertThat(basePoint.path("actual").decimalValue()).isEqualByComparingTo(overview);
    assertThat(target.path("baselineWarning").isMissingNode() || target.path("baselineWarning").isNull()).isFalse(); // 90 vs ~7 t

    // A base value far below what was measured (the tester's 90 t vs 1,917 t) gave −4,060% and a 500 (numeric overflow):
    // progress is held to ±999.99% and the target says the base value looks wrong.
    JsonNode tiny = data(postJson("/v1/carbon/targets", "{\"targetName\":\"Tiny baseline " + DAY.incrementAndGet() + "\","
        + "\"targetType\":\"absolute\",\"scope\":\"scope_1_2\",\"baseYear\":" + year + ",\"baseValue\":0.01,\"targetYear\":" + (year + 4)
        + ",\"targetValue\":0.005,\"pathway\":\"linear\"}").andExpect(status().isCreated()));
    assertThat(tiny.path("progressPct").decimalValue()).isEqualByComparingTo("-999.99");
    assertThat(tiny.path("status").asText()).isEqualTo("off_track");
    assertThat(tiny.path("baselineWarning").asText()).contains("differ from the base value entered (0.01)");
    mvc.perform(get("/v1/carbon/targets").header("Authorization", director())).andExpect(status().isOk());
  }

  @Test
  void monthlyEmissionsSplitMultiMonthRecordsByDays() throws Exception {
    // F15: a Q1 record (90 days) is spread over January (31), February (28) and March (31), not put in January.
    int year = 2087;
    emission("{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"diesel_generator\",\"activityData\":9000,"
        + "\"reportingPeriodStart\":\"" + year + "-01-01\",\"reportingPeriodEnd\":\"" + year + "-03-31\"}");
    JsonNode summary = data(mvc.perform(get("/v1/carbon/emissions/summary?year=" + year).header("Authorization", director())));
    java.util.Map<String, java.math.BigDecimal> months = new java.util.HashMap<>();
    summary.path("byMonth").forEach(m -> months.put(m.path("key").asText(), m.path("co2eTonnes").decimalValue()));
    java.math.BigDecimal total = summary.path("scope1").decimalValue(); // 24.12 t
    assertThat(months.get(year + "-01")).isEqualByComparingTo(total.multiply(java.math.BigDecimal.valueOf(31))
        .divide(java.math.BigDecimal.valueOf(90), 6, java.math.RoundingMode.HALF_UP));
    assertThat(months.get(year + "-02")).isEqualByComparingTo(total.multiply(java.math.BigDecimal.valueOf(28))
        .divide(java.math.BigDecimal.valueOf(90), 6, java.math.RoundingMode.HALF_UP));
    assertThat(months.values().stream().reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
        .isCloseTo(total, org.assertj.core.data.Offset.offset(new java.math.BigDecimal("0.000005")));
  }

  @Test
  void financedSectorComesFromTheTaxonomy() throws Exception {
    // F9: free text is refused; the ISIC section code is stored.
    postJson("/v1/carbon/financed", "{\"reportingYear\":2026,\"assetClass\":\"business_loans_unlisted_equity\",\"counterparty\":\"x\","
        + "\"sector\":\"Power\",\"outstandingAmount\":1,\"denominatorValue\":2,\"currency\":\"NGN\",\"counterpartyEmissions\":1,"
        + "\"dataQualityScore\":3,\"emissionsSource\":\"x\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.sector").value("Choose a sector from the list"));
  }

  @Test
  void customFactorsUseKnownActivitiesCountriesAndUnits() throws Exception {
    // F11: unknown country, a unit that doesn't fit the activity, and a new category without its name and scope.
    postJson("/v1/carbon/factors", "{\"region\":\"ZZ\",\"category\":\"diesel_generator\",\"factorValue\":2.7,\"unit\":\"kgCO2e/litre\","
        + "\"source\":\"s\",\"validFrom\":\"2026-01-01\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.region").exists());
    postJson("/v1/carbon/factors", "{\"region\":\"NG\",\"category\":\"diesel_generator\",\"factorValue\":2.7,\"unit\":\"kgCO2e/kg\","
        + "\"source\":\"s\",\"validFrom\":\"2026-01-01\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.unit").value("must be kgCO2e/litre for Diesel (AGO) in generators"));
    String category = "captive_turbine_" + DAY.incrementAndGet();
    postJson("/v1/carbon/factors", "{\"region\":\"NG\",\"category\":\"" + category + "\",\"factorValue\":0.4,\"unit\":\"kgCO2e/kWh\","
        + "\"source\":\"s\",\"validFrom\":\"2026-01-01\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.activityLabel").exists()).andExpect(jsonPath("$.errors.activityScope").exists());
    postJson("/v1/carbon/factors", "{\"region\":\"GH\",\"category\":\"" + category + "\",\"factorValue\":0.4,\"unit\":\"kgCO2e/kWh\","
        + "\"source\":\"OEM datasheet\",\"validFrom\":\"2026-01-01\",\"activityLabel\":\"Captive gas turbine\",\"activityScope\":\"scope_1\"}")
        .andExpect(status().isCreated());
    JsonNode activities = data(mvc.perform(get("/v1/carbon/factors/activities").header("Authorization", director())));
    boolean found = false;
    for (JsonNode a : activities) {
      if (a.path("category").asText().equals(category)) {
        assertThat(a.path("scope").asText()).isEqualTo("scope_1");
        assertThat(a.path("activityUnit").asText()).isEqualTo("kWh");
        assertThat(a.path("custom").asBoolean()).isTrue();
        found = true;
      }
    }
    assertThat(found).isTrue();
    // The country list is ISO 3166 plus the factor regions.
    JsonNode regions = data(mvc.perform(get("/v1/carbon/factors/regions").header("Authorization", director())));
    assertThat(regions.size()).isGreaterThan(240);
    assertThat(regions.toString()).contains("\"code\":\"NG\",\"name\":\"Nigeria\"").contains("WEST_AFRICA");
  }

  @Test
  void previewCalculatesWithoutSaving() throws Exception {
    String body = "{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"heavy_fuel_oil\","
        + "\"activityData\":1000,\"reportingPeriodStart\":\"2026-08-15\",\"reportingPeriodEnd\":\"2026-08-15\"}";
    JsonNode p = data(postJson("/v1/carbon/emissions/preview", body).andExpect(status().isOk()));
    assertThat(p.path("co2eTonnes").decimalValue()).isEqualByComparingTo("3.074445");
    assertThat(p.path("perGas").asBoolean()).isTrue();
    assertThat(p.path("activityUnit").asText()).isEqualTo("litre");
    assertThat(p.path("factor").path("source").asText()).startsWith("IPCC 2006");
    assertThat(p.path("factor").path("reviewStatus").asText()).isEqualTo("pending_sme");
    JsonNode list = data(mvc.perform(get("/v1/carbon/emissions?from=2026-08-15&to=2026-08-15").header("Authorization", director())));
    for (JsonNode r : list.path("content")) {
      assertThat(r.path("sourceActivity").asText()).isNotEqualTo("heavy_fuel_oil");
    }
  }

  @Test
  void journey6DieselGeneratorAndPerGasFactors() throws Exception {
    JsonNode ng = emission("{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"diesel_generator\","
        + "\"activityData\":1000," + period() + "}");
    assertThat(ng.path("co2eTonnes").decimalValue()).isEqualByComparingTo("2.68");
    assertThat(ng.path("activityUnit").asText()).isEqualTo("litre");

    // Kerosene has only the GLOBAL IPCC per-gas factor: gases are kept and weighted with the tenant's GWP set (AR6).
    JsonNode kero = emission("{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"kerosene\","
        + "\"activityData\":1000," + period() + "}");
    assertThat(kero.path("factor").path("level").asText()).isEqualTo("global");
    assertThat(kero.path("gwpSet").asText()).isEqualTo("AR6");
    assertThat(kero.path("co2eTonnes").decimalValue()).isEqualByComparingTo("2.528248");
    assertThat(kero.path("co2Tonnes").decimalValue()).isEqualByComparingTo("2.519376");
    assertThat(kero.path("ch4Tonnes").decimalValue()).isEqualByComparingTo("0.000105");
  }

  @Test
  void validationScopeUnitsAndMissingFactors() throws Exception {
    postJson("/v1/carbon/emissions", "{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_4\",\"sourceActivity\":\"diesel_generator\","
        + "\"activityData\":1," + period() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.scope").exists());
    postJson("/v1/carbon/emissions", "{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_3\",\"sourceActivity\":\"air_travel_longhaul\","
        + "\"activityData\":1," + period() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.scope3Category").exists());
    postJson("/v1/carbon/emissions", "{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"diesel_generator\","
        + "\"activityData\":1,\"activityUnit\":\"kg\"," + period() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.activityUnit").value("must be litre for this factor (kgCO2e/litre)"));
    postJson("/v1/carbon/emissions", "{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"unicorn_fuel\","
        + "\"activityData\":1," + period() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.sourceActivity").exists());
    postJson("/v1/carbon/emissions", "{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"diesel_generator\","
        + "\"activityData\":-5," + period() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.activityData").exists());
    mvc.perform(post("/v1/carbon/emissions").header("Authorization", bearerForRole("AUDITOR")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"diesel_generator\",\"activityData\":1," + period() + "}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void journey8Scope3SpendBasedWithATenantSpendFactor() throws Exception {
    // The tenant enters its own spend factor, with its source, for a new Scope 3 category 1 activity (F11).
    String category = "purchased_goods_spend_" + DAY.incrementAndGet();
    data(postJson("/v1/carbon/factors", "{\"region\":\"NG\",\"category\":\"" + category + "\",\"factorValue\":0.5,"
        + "\"unit\":\"kgCO2e/USD\",\"source\":\"Supplier LCA 2026\",\"validFrom\":\"2026-01-01\",\"factorMethod\":\"spend\","
        + "\"spendCurrency\":\"USD\",\"activityLabel\":\"Purchased goods\",\"activityScope\":\"scope_3\",\"activityScope3Category\":1}")
        .andExpect(status().isCreated()));
    JsonNode r = emission("{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_3\",\"scope3Category\":1,\"calculationMethod\":\"spend_based\","
        + "\"sourceActivity\":\"" + category + "\",\"spendAmount\":2000,\"spendCurrency\":\"USD\"," + period() + "}");
    assertThat(r.path("co2eTonnes").decimalValue()).isEqualByComparingTo("1");
    assertThat(r.path("calculationMethod").asText()).isEqualTo("spend_based");
    assertThat(r.path("factor").path("level").asText()).isEqualTo("custom");
    assertThat(r.path("scope3Category").asInt()).isEqualTo(1);

    // Spend in another currency converts with the dated tenant rate; without one, it's refused.
    postJson("/v1/carbon/emissions", "{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_3\",\"scope3Category\":1,"
        + "\"calculationMethod\":\"spend_based\",\"sourceActivity\":\"" + category + "\",\"spendAmount\":2000,\"spendCurrency\":\"EUR\","
        + period() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.spendCurrency").exists());
    // An activity factor can't be used spend-based (and vice versa).
    postJson("/v1/carbon/emissions", "{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"calculationMethod\":\"spend_based\","
        + "\"sourceActivity\":\"diesel_generator\",\"spendAmount\":10,\"spendCurrency\":\"USD\"," + period() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.sourceActivity").exists());
  }

  @Test
  void recalculationPicksUpANewCustomFactorAndTheGwpSet() throws Exception {
    LocalDate day = LocalDate.of(2026, 11, 3);
    String p = "\"reportingPeriodStart\":\"" + day + "\",\"reportingPeriodEnd\":\"" + day + "\"";
    JsonNode lpg = emission("{\"orgId\":\"" + ORG_SITE_2 + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"lpg\",\"activityData\":100," + p + "}");
    assertThat(lpg.path("factor").path("level").asText()).isEqualTo("global");
    assertThat(lpg.path("co2eTonnes").decimalValue()).isEqualByComparingTo("0.298733");
    // S7: platform factors are still pending expert review, so the record is provisional.
    assertThat(lpg.path("factor").path("reviewStatus").asText()).isEqualTo("pending_sme");

    // A tenant factor for LPG in Nigeria now applies: recalculation switches to it and says so.
    postJson("/v1/carbon/factors", "{\"region\":\"NG\",\"category\":\"lpg\",\"factorValue\":3.1,\"unit\":\"kgCO2e/kg\","
        + "\"source\":\"Supplier certificate\",\"validFrom\":\"2026-11-01\",\"validTo\":\"2026-11-30\"}").andExpect(status().isCreated());
    JsonNode recalc = data(postJson("/v1/carbon/calculate", "{\"from\":\"" + day + "\",\"to\":\"" + day + "\"}").andExpect(status().isOk()));
    assertThat(recalc.path("updated").asInt()).isGreaterThanOrEqualTo(1);
    JsonNode after = data(mvc.perform(get("/v1/carbon/emissions/" + lpg.path("id").asText()).header("Authorization", director())));
    assertThat(after.path("factor").path("level").asText()).isEqualTo("custom");
    assertThat(after.path("co2eTonnes").decimalValue()).isEqualByComparingTo("0.31");
    assertThat(after.path("factor").path("reviewStatus").asText()).isEqualTo("tenant_provided");

    // Verified records are left alone.
    mvc.perform(put("/v1/carbon/emissions/" + lpg.path("id").asText() + "/verify").header("Authorization", director())
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + after.path("version").asLong() + "}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.verified").value(true));
    mvc.perform(put("/v1/carbon/emissions/" + lpg.path("id").asText()).header("Authorization", director())
            .contentType(MediaType.APPLICATION_JSON).content("{\"orgId\":\"" + ORG_SITE_2 + "\",\"scope\":\"scope_1\","
                + "\"sourceActivity\":\"lpg\",\"activityData\":1," + p + ",\"version\":" + (after.path("version").asLong() + 1) + "}"))
        .andExpect(status().isConflict());
  }

  @Test
  void approvedDataHubActivityBecomesAnEmissionRecordAndReopeningRemovesIt() throws Exception {
    String collector = bearerForRole("DATA_COLLECTOR");
    String p = "\"reportingPeriodStart\":\"2026-12-05\",\"reportingPeriodEnd\":\"2026-12-05\"";
    JsonNode dp = data(mvc.perform(post("/v1/data/points").header("Authorization", collector).contentType(MediaType.APPLICATION_JSON)
        .content("{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"electricity_kwh\",\"numericValue\":2000," + p + "}")));
    String id = dp.path("id").asText();
    dp = data(mvc.perform(put("/v1/data/points/" + id + "/submit").header("Authorization", collector)
        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + dp.path("version").asLong() + "}")));
    dp = data(mvc.perform(put("/v1/data/points/" + id + "/validate").header("Authorization", bearerForRole("ESG_ANALYST"))
        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + dp.path("version").asLong() + "}")));
    dp = data(mvc.perform(put("/v1/data/points/" + id + "/approve").header("Authorization", director())
        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + dp.path("version").asLong() + "}")));

    JsonNode records = data(mvc.perform(get("/v1/carbon/emissions?source=data_hub&from=2026-12-05&to=2026-12-05")
        .header("Authorization", director()))).path("content");
    JsonNode record = null;
    for (JsonNode r : records) {
      if (id.equals(r.path("dataPointId").asText())) {
        record = r;
      }
    }
    assertThat(record).as("emission record for the approved point").isNotNull();
    assertThat(record.path("scope").asText()).isEqualTo("scope_2");
    assertThat(record.path("co2eTonnes").decimalValue()).isEqualByComparingTo("0.86");
    // Lineage (TRD §6.6.2): the record links back to the data point and to the factor it was calculated from.
    mvc.perform(get("/v1/audit/lineage?type=emission_record&id=" + record.path("id").asText()).header("Authorization", director()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.upstream[?(@.sourceType == 'data_point' && @.sourceId == '" + id + "')].relation").value("calculated_from"))
        .andExpect(jsonPath("$.data.upstream[?(@.sourceType == 'emission_factor')]").isNotEmpty());
    // Editing it directly is refused: the Data Hub point is the source.
    mvc.perform(put("/v1/carbon/emissions/" + record.path("id").asText()).header("Authorization", director())
            .contentType(MediaType.APPLICATION_JSON).content("{\"orgId\":\"" + ORG_SITE_1 + "\",\"scope\":\"scope_2\","
                + "\"sourceActivity\":\"electricity_grid\",\"activityData\":1," + p + ",\"version\":0}"))
        .andExpect(status().isConflict());

    mvc.perform(put("/v1/data/points/" + id + "/reopen").header("Authorization", director()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":" + dp.path("version").asLong() + ",\"reason\":\"meter misread\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/v1/carbon/emissions/" + record.path("id").asText()).header("Authorization", director()))
        .andExpect(status().isNotFound());
  }

  @Test
  void journey7GasFlaringByComposition() throws Exception {
    String body = "{\"facilityId\":\"" + ORG_SITE_2 + "\",\"volumeMcf\":1000,\"gasComposition\":{\"methane\":85,\"ethane\":7,"
        + "\"propane\":3,\"butane\":1,\"pentane_plus\":0.5,\"co2\":2,\"nitrogen\":1.5},\"flaringReason\":\"routine\","
        + "\"reportingPeriodStart\":\"2026-06-01\",\"reportingPeriodEnd\":\"2026-06-30\"}";
    JsonNode f = data(postJson("/v1/carbon/flaring", body).andExpect(status().isCreated()));
    assertThat(f.path("co2eTonnes").decimalValue()).isEqualByComparingTo("69.828118");
    assertThat(f.path("combustionEfficiency").decimalValue()).isEqualByComparingTo("0.98");
    assertThat(f.path("emissionRecordId").asText()).isNotBlank();
    mvc.perform(get("/v1/carbon/emissions/" + f.path("emissionRecordId").asText()).header("Authorization", director()))
        .andExpect(jsonPath("$.data.scope").value("scope_1"))
        .andExpect(jsonPath("$.data.sourceActivity").value("gas_flaring"))
        .andExpect(jsonPath("$.data.calculationMethod").value("flaring_composition"))
        .andExpect(jsonPath("$.data.co2eTonnes").value(69.828118));

    // Composition must add up to 100; unknown components are refused.
    postJson("/v1/carbon/flaring", body.replace("\"nitrogen\":1.5", "\"nitrogen\":5"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.gasComposition").exists());
    postJson("/v1/carbon/flaring", body.replace("\"nitrogen\":1.5", "\"argon\":1.5"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.gasComposition").exists());

    // The satellite adapter is a labelled sandbox that never verifies anything.
    JsonNode checked = data(mvc.perform(post("/v1/carbon/flaring/" + f.path("id").asText() + "/satellite-check")
        .header("Authorization", director())).andExpect(status().isOk()));
    assertThat(checked.path("satelliteCheck").path("provider").asText()).isEqualTo("Sandbox");
    assertThat(checked.path("satelliteCheck").path("status").asText()).isEqualTo("not_connected");
    assertThat(checked.path("satelliteVerified").asBoolean()).isFalse();
  }

  @Test
  void marketBasedScope2SubtractsRecCoveredElectricity() throws Exception {
    String p = "\"reportingPeriodStart\":\"2027-02-01\",\"reportingPeriodEnd\":\"2027-02-28\"";
    emission("{\"orgId\":\"" + ORG_SITE_1 + "\",\"scope\":\"scope_2\",\"sourceActivity\":\"electricity_grid\",\"activityData\":10000," + p + "}");
    JsonNode before = data(mvc.perform(get("/v1/carbon/emissions/summary?year=2027").header("Authorization", director())));
    JsonNode rec = data(postJson("/v1/carbon/instruments", "{\"orgId\":\"" + ORG_SITE_1 + "\",\"instrumentType\":\"rec\","
        + "\"energySource\":\"solar\",\"mwh\":5,\"supplier\":\"Lagos Solar REC Registry\",\"certificateRef\":\"REC-2027-001\"," + p + "}")
        .andExpect(status().isCreated()));
    assertThat(rec.path("coveredMwh").decimalValue()).isEqualByComparingTo("5");
    assertThat(rec.path("avoidedTonnes").decimalValue()).isEqualByComparingTo("2.15");
    JsonNode after = data(mvc.perform(get("/v1/carbon/emissions/summary?year=2027").header("Authorization", director())));
    assertThat(after.path("scope2Location").decimalValue()).isEqualByComparingTo(before.path("scope2Location").decimalValue());
    assertThat(before.path("scope2Location").decimalValue().subtract(after.path("scope2Market").decimalValue()))
        .isEqualByComparingTo("2.15");
    // The same certificate can't be counted twice.
    postJson("/v1/carbon/instruments", "{\"orgId\":\"" + ORG_SITE_1 + "\",\"instrumentType\":\"rec\",\"energySource\":\"solar\","
        + "\"mwh\":1,\"supplier\":\"x\",\"certificateRef\":\"REC-2027-001\"," + p + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.certificateRef").exists());
  }

  @Test
  void journey9TargetProgressAgainstActuals() throws Exception {
    // Scope 3 in 2028: one record of 10 t → measured against a 2027 base of 40 t heading to 20 t in 2030.
    emission("{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_3\",\"scope3Category\":6,\"sourceActivity\":\"air_travel_longhaul\","
        + "\"activityData\":51282.051282,\"activityUnit\":\"km\",\"reportingPeriodStart\":\"2028-03-01\",\"reportingPeriodEnd\":\"2028-03-31\"}");
    JsonNode t = data(postJson("/v1/carbon/targets", "{\"targetName\":\"Halve business travel\",\"targetType\":\"absolute\","
        + "\"scope\":\"scope_3\",\"baseYear\":2027,\"baseValue\":40,\"targetYear\":2030,\"targetValue\":20,\"pathway\":\"linear\","
        + "\"sbtiAligned\":true}").andExpect(status().isCreated()));
    assertThat(t.path("progressYear").asInt()).isEqualTo(2028);
    assertThat(t.path("currentValue").decimalValue()).isEqualByComparingTo("10");
    assertThat(t.path("status").asText()).isEqualTo("achieved");
    assertThat(t.path("annualReductionPct").decimalValue()).isEqualByComparingTo("16.67");
    assertThat(t.path("pathwayPoints").size()).isEqualTo(4);
    assertThat(t.path("sbtiFindings").isArray()).isTrue();

    postJson("/v1/carbon/targets", "{\"targetName\":\"x\",\"targetType\":\"absolute\",\"scope\":\"scope_1\",\"baseYear\":2030,"
        + "\"baseValue\":10,\"targetYear\":2025,\"targetValue\":12}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.targetYear").exists())
        .andExpect(jsonPath("$.errors.targetValue").exists());
    postJson("/v1/carbon/targets", "{\"targetName\":\"x\",\"targetType\":\"intensity\",\"scope\":\"scope_1\",\"baseYear\":2025,"
        + "\"baseValue\":10,\"targetYear\":2030,\"targetValue\":5}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.intensityDenominator").exists());
  }

  @Test
  void pcafFinancedEmissions() throws Exception {
    JsonNode pos = data(postJson("/v1/carbon/financed", "{\"reportingYear\":2026,\"assetClass\":\"business_loans_unlisted_equity\","
        + "\"counterparty\":\"Ikeja Cement Plc\",\"sector\":\"C\",\"outstandingAmount\":25000000,\"denominatorValue\":100000000,"
        + "\"currency\":\"NGN\",\"counterpartyEmissions\":40000,\"dataQualityScore\":2,\"emissionsSource\":\"Audited GHG report 2025\"}")
        .andExpect(status().isCreated()));
    assertThat(pos.path("attributionFactor").decimalValue()).isEqualByComparingTo("0.25");
    assertThat(pos.path("financedEmissions").decimalValue()).isEqualByComparingTo("10000");
    mvc.perform(get("/v1/carbon/financed/summary?year=2026").header("Authorization", director()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.byAssetClass[?(@.assetClass == 'business_loans_unlisted_equity')].financedEmissions").exists());
    postJson("/v1/carbon/financed", "{\"reportingYear\":2026,\"assetClass\":\"mortgages\",\"counterparty\":\"x\","
        + "\"outstandingAmount\":200,\"denominatorValue\":100,\"currency\":\"NGN\",\"counterpartyEmissions\":1,\"dataQualityScore\":5,"
        + "\"emissionsSource\":\"x\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.outstandingAmount").exists());
    postJson("/v1/carbon/financed", "{\"reportingYear\":2026,\"assetClass\":\"crypto\",\"counterparty\":\"x\",\"outstandingAmount\":1,"
        + "\"denominatorValue\":100,\"currency\":\"NGN\",\"counterpartyEmissions\":1,\"dataQualityScore\":6,\"emissionsSource\":\"x\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.assetClass").exists())
        .andExpect(jsonPath("$.errors.dataQualityScore").exists());
  }

  @Test
  void dashboardSummaryAndIntensity() throws Exception {
    // Revenue for 2029, approved in the Data Hub, gives intensity per million NGN.
    String collector = bearerForRole("DATA_COLLECTOR");
    JsonNode dp = data(mvc.perform(post("/v1/data/points").header("Authorization", collector).contentType(MediaType.APPLICATION_JSON)
        .content("{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"revenue\",\"numericValue\":500000000,"
            + "\"reportingPeriodStart\":\"2029-01-01\",\"reportingPeriodEnd\":\"2029-12-31\"}")));
    String id = dp.path("id").asText();
    dp = data(mvc.perform(put("/v1/data/points/" + id + "/submit").header("Authorization", collector)
        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")));
    dp = data(mvc.perform(put("/v1/data/points/" + id + "/validate").header("Authorization", bearerForRole("ESG_ANALYST"))
        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + dp.path("version").asLong() + "}")));
    mvc.perform(put("/v1/data/points/" + id + "/approve").header("Authorization", director())
        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + dp.path("version").asLong() + "}")).andExpect(status().isOk());
    emission("{\"orgId\":\"" + ORG_SITE_1 + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"diesel_generator\",\"activityData\":1000,"
        + "\"reportingPeriodStart\":\"2029-05-01\",\"reportingPeriodEnd\":\"2029-05-31\"}");

    JsonNode s = data(mvc.perform(get("/v1/carbon/emissions/summary?year=2029").header("Authorization", director()))
        .andExpect(status().isOk()));
    assertThat(s.path("scope1").decimalValue()).isEqualByComparingTo("2.68");
    assertThat(s.path("byMonth").get(0).path("key").asText()).isEqualTo("2029-05");
    JsonNode revenue = null;
    for (JsonNode i : s.path("intensity")) {
      if ("revenue".equals(i.path("denominator").asText())) {
        revenue = i;
      }
    }
    assertThat(revenue).isNotNull();
    assertThat(revenue.path("tonnesPerUnit").decimalValue()).isEqualByComparingTo("0.00536");
    mvc.perform(get("/v1/carbon/emissions/summary?year=1999").header("Authorization", director()))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.year").exists());
  }
}
