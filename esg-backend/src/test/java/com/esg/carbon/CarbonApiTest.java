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
  void previewCalculatesWithoutSaving() throws Exception {
    String body = "{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"heavy_fuel_oil\","
        + "\"activityData\":1000,\"reportingPeriodStart\":\"2026-08-15\",\"reportingPeriodEnd\":\"2026-08-15\"}";
    JsonNode p = data(postJson("/v1/carbon/emissions/preview", body).andExpect(status().isOk()));
    assertThat(p.path("co2eTonnes").decimalValue()).isEqualByComparingTo("3.074445");
    assertThat(p.path("perGas").asBoolean()).isTrue();
    assertThat(p.path("activityUnit").asText()).isEqualTo("litre");
    assertThat(p.path("factor").path("source").asText()).startsWith("IPCC 2006");
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
    // No EEIO dataset is loaded (WP-17): the tenant enters its own spend factor, with its source.
    String category = "purchased_goods_spend_" + DAY.incrementAndGet();
    data(postJson("/v1/carbon/factors", "{\"region\":\"NG\",\"category\":\"" + category + "\",\"factorValue\":0.5,"
        + "\"unit\":\"kgCO2e/USD\",\"source\":\"Supplier LCA 2026\",\"validFrom\":\"2026-01-01\",\"factorMethod\":\"spend\","
        + "\"spendCurrency\":\"USD\"}").andExpect(status().isCreated()));
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

    // A tenant factor for LPG in Nigeria now applies: recalculation switches to it and says so.
    postJson("/v1/carbon/factors", "{\"region\":\"NG\",\"category\":\"lpg\",\"factorValue\":3.1,\"unit\":\"kgCO2e/kg\","
        + "\"source\":\"Supplier certificate\",\"validFrom\":\"2026-11-01\",\"validTo\":\"2026-11-30\"}").andExpect(status().isCreated());
    JsonNode recalc = data(postJson("/v1/carbon/recalculate", "{\"from\":\"" + day + "\",\"to\":\"" + day + "\"}").andExpect(status().isOk()));
    assertThat(recalc.path("updated").asInt()).isGreaterThanOrEqualTo(1);
    JsonNode after = data(mvc.perform(get("/v1/carbon/emissions/" + lpg.path("id").asText()).header("Authorization", director())));
    assertThat(after.path("factor").path("level").asText()).isEqualTo("custom");
    assertThat(after.path("co2eTonnes").decimalValue()).isEqualByComparingTo("0.31");

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
        + "\"counterparty\":\"Ikeja Cement Plc\",\"sector\":\"Cement\",\"outstandingAmount\":25000000,\"denominatorValue\":100000000,"
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
