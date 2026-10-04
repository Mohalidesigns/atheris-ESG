package com.esg.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.reporting.ReportingDtos.VariableValue;
import com.esg.reporting.VariableEngine;
import com.esg.security.TenantContext;
import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** WP-32 M5 Risk & Materiality through the API: double materiality (journey 14), register and heatmaps, climate scenarios. */
class RiskApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;
  @Autowired private VariableEngine variables;

  private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Lagos"));

  private JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  private ResultActions send(MockHttpServletRequestBuilder b, String token, String body) throws Exception {
    return mvc.perform(b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode read(String path, String token) throws Exception {
    return data(mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk()));
  }

  private static String assessment(String name, int year, String blend, String stakeholders) {
    return "{\"name\":\"" + name + "\",\"reportingYear\":" + year + ",\"impactThreshold\":3,\"financialThreshold\":3,\"stakeholderBlend\":" + blend
        + ",\"stakeholders\":[" + stakeholders + "]}";
  }

  private static final String GROUPS = "{\"group\":\"investors\",\"weight\":40},{\"group\":\"communities\",\"weight\":60}";

  private static String topic(String name, String type, int scale, int scope, int irr, Integer likelihood, int mag, int finL, String ratings) {
    return "{\"topic\":\"" + name + "\",\"category\":\"E\",\"griTopic\":\"GRI 305\",\"impactType\":\"" + type + "\",\"severityScale\":" + scale
        + ",\"severityScope\":" + scope + ",\"severityIrremediability\":" + irr + (likelihood == null ? "" : ",\"impactLikelihood\":" + likelihood)
        + ",\"financialMagnitude\":" + mag + ",\"financialLikelihood\":" + finL + ",\"ratings\":{" + ratings + "}}";
  }

  private static Map<String, JsonNode> byTopic(JsonNode a) {
    Map<String, JsonNode> out = new HashMap<>();
    a.path("topicList").forEach(t -> out.put(t.path("topic").asText(), t));
    return out;
  }

  private String requirementStatus(String framework, String code, int year) throws Exception {
    for (JsonNode r : read("/v1/compliance/gaps?framework=" + framework + "&year=" + year, bearerForRole("COMPLIANCE_OFFICER")).path("requirements")) {
      if (code.equals(r.path("code").asText())) {
        return r.path("status").asText();
      }
    }
    throw new AssertionError("no " + code);
  }

  // ---------------------------------------------------------------- journey 14: materiality → matrix from real scores

  @Test
  void journey14MaterialityMatrixFromRealScoresApprovedBySomeoneElse() throws Exception {
    int year = 2023;
    asOwnerInTenantA("update risk.risk_assessments set deleted_at = now(), status = 'draft' where deleted_at is null and reporting_year = " + year);
    String manager = bearerForRole("ESG_MANAGER");
    String director = bearerForRole("ESG_DIRECTOR");
    send(post("/v1/risk/assessments"), manager, assessment("DMA " + year, year, "0.25", GROUPS + ",{\"group\":\"investors\",\"weight\":10}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors['stakeholders[2].group']").exists());
    send(post("/v1/risk/assessments"), manager, assessment("DMA " + year, year, "0.25", "")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.stakeholderBlend").exists());
    send(post("/v1/risk/assessments"), manager, assessment("DMA " + year, year, "0.6", GROUPS)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.stakeholderBlend").exists());
    send(post("/v1/risk/assessments"), manager, assessment("DMA 2020", 2020, "0", GROUPS)).andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ESG-409-LOCKED"));
    send(post("/v1/risk/assessments"), bearerForRole("ESG_ANALYST"), assessment("DMA x", year, "0", GROUPS)).andExpect(status().isForbidden());

    assertThat(requirementStatus("GRI", "3-2", year)).isEqualTo("missing");
    JsonNode a = data(send(post("/v1/risk/assessments"), manager, assessment("DMA " + year, year, "0.25", GROUPS)).andExpect(status().isCreated()));
    String path = "/v1/risk/assessments/" + a.path("id").asText();
    send(post(path + "/topics"), manager, topic("Water", "potential", 4, 3, 5, null, 4, 4, "")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.impactLikelihood").exists());
    send(post(path + "/topics"), manager, topic("Water", "actual", 4, 3, 5, null, 4, 4, "\"regulators\":3")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.ratings").exists());
    send(post(path + "/topics"), manager, topic("Water", "actual", 6, 3, 5, null, 4, 4, "")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.severityScale").exists());
    send(post(path + "/topics"), manager, topic("Climate change", "actual", 4, 3, 5, null, 4, 4, "\"investors\":5,\"communities\":3"))
        .andExpect(status().isCreated());
    send(post(path + "/topics"), manager, topic("Water", "potential", 4, 3, 5, 3, 4, 4, "")).andExpect(status().isCreated());
    a = data(send(post(path + "/topics"), manager, topic("Biodiversity", "potential", 2, 2, 3, 2, 1, 1, "\"communities\":2")).andExpect(status().isCreated()));

    // The matrix comes from the stored scores (RiskMath; golden values in RiskMathTest).
    Map<String, JsonNode> t = byTopic(a);
    assertThat(t.get("Climate change").path("position").path("impactPosition").decimalValue()).isEqualByComparingTo("3.95");
    assertThat(t.get("Climate change").path("position").path("financial").decimalValue()).isEqualByComparingTo("3.20");
    assertThat(t.get("Climate change").path("position").path("quadrant").asText()).isEqualTo("double");
    assertThat(t.get("Water").path("position").path("impactPosition").decimalValue()).isEqualByComparingTo("2.40");
    assertThat(t.get("Water").path("position").path("quadrant").asText()).isEqualTo("financial");
    // Biodiversity: impact 0.93 pulled by the communities' 2 → 0.93 × 0.75 + 2 × 0.25 = 1.20.
    assertThat(t.get("Biodiversity").path("position").path("impactPosition").decimalValue()).isEqualByComparingTo("1.20");
    assertThat(a.path("materialTopics").asInt()).isEqualTo(2);
    assertThat(a.path("topicList").get(0).path("topic").asText()).isEqualTo("Climate change");

    // Re-weighting moves the matrix: without the stakeholder blend Climate change sits at its own impact score.
    a = data(send(put(path), manager, assessment("DMA " + year, year, "0", GROUPS).replace("]}", "],\"version\":" + a.path("version").asLong() + "}"))
        .andExpect(status().isOk()));
    assertThat(byTopic(a).get("Climate change").path("position").path("impactPosition").decimalValue()).isEqualByComparingTo("4.00");

    // Submit → frozen; the manager can't approve; the director approves.
    a = data(send(post(path + "/actions"), manager, "{\"action\":\"submit\",\"version\":" + a.path("version").asLong() + "}").andExpect(status().isOk()));
    assertThat(a.path("status").asText()).isEqualTo("in_review");
    send(post(path + "/topics"), manager, topic("Waste", "actual", 2, 2, 2, null, 2, 2, "")).andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ESG-409-STATE"));
    send(post(path + "/actions"), manager, "{\"action\":\"approve\",\"version\":" + a.path("version").asLong() + "}").andExpect(status().isForbidden());
    a = data(send(post(path + "/actions"), director, "{\"action\":\"approve\",\"version\":" + a.path("version").asLong() + "}").andExpect(status().isOk()));
    assertThat(a.path("status").asText()).isEqualTo("approved");
    assertThat(a.path("approvedByName").asText()).isEqualTo("Esg Director Fixture");

    // The approved assessment evidences GRI 3-1 / 3-2 (narrative: partial until attested) and fills report variables.
    assertThat(requirementStatus("GRI", "3-2", year)).isEqualTo("partial");
    assertThat(requirementStatus("GRI", "3-1", year)).isEqualTo("partial");
    Map<String, VariableValue> v = asTenantA(() -> variables.resolve(Set.of("risk:material_topics", "risk:material_topic_list"),
        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), "t"));
    assertThat(v.get("risk:material_topics").value()).isEqualByComparingTo("2");
    assertThat(v.get("risk:material_topic_list").display()).isEqualTo("Climate change, Water");

    // The submitter can't approve their own assessment; reopening needs a reason.
    JsonNode b = data(send(post("/v1/risk/assessments"), director, assessment("DMA second " + year, year, "0", GROUPS)).andExpect(status().isCreated()));
    String bp = "/v1/risk/assessments/" + b.path("id").asText();
    b = data(send(post(bp + "/topics"), director, topic("Climate change", "actual", 4, 3, 5, null, 4, 4, "")));
    send(post(bp + "/actions"), director, "{\"action\":\"submit\",\"version\":" + b.path("version").asLong() + "}").andExpect(status().isOk());
    b = read(bp, director);
    assertThat(b.path("blockedActions").path("approve").asText()).contains("someone else");
    send(post(bp + "/actions"), director, "{\"action\":\"approve\",\"version\":" + b.path("version").asLong() + "}").andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ESG-409-SOD"));
    send(post(path + "/actions"), director, "{\"action\":\"reopen\",\"version\":" + a.path("version").asLong() + "}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.note").exists());
  }

  private <T> T asTenantA(java.util.concurrent.Callable<T> work) throws Exception {
    String t = TenantContext.getTenantId();
    String u = TenantContext.getUserId();
    TenantContext.setTenantId(PostgresTestDatabase.TENANT_A);
    TenantContext.setUserId(roleUserId("ESG_DIRECTOR"));
    try {
      return work.call();
    } finally {
      TenantContext.setTenantId(t);
      TenantContext.setUserId(u);
    }
  }

  // ---------------------------------------------------------------- register and heatmaps

  private static String risk(String org, String title, String category, String sector, int l, int i, Integer rl, Integer ri) {
    return "{\"orgId\":\"" + org + "\",\"title\":\"" + title + "\",\"category\":\"" + category + "\"" + (sector == null ? "" : ",\"sectorCode\":\"" + sector + "\"")
        + ",\"timeHorizon\":\"medium\",\"likelihood\":" + l + ",\"impact\":" + i + (rl == null ? "" : ",\"residualLikelihood\":" + rl + ",\"residualImpact\":" + ri)
        + ",\"status\":\"open\"}";
  }

  @Test
  void riskRegisterFeedsHeatmapsAtPortfolioEntityAndSectorLevel() throws Exception {
    asOwnerInTenantA("update risk.risk_register set deleted_at = now() where deleted_at is null");
    String manager = bearerForRole("ESG_MANAGER");
    String director = bearerForRole("ESG_DIRECTOR");
    send(post("/v1/risk/risks"), manager, risk(ORG_SITE_1, "x", "climate_physical", null, 2, 2, 3, 3)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.residualImpact").exists());
    send(post("/v1/risk/risks"), manager, risk(ORG_SITE_1, "x", "climate_physical", null, 2, 2, null, null).replace("\"status\"",
        "\"residualLikelihood\":1,\"status\"")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.residualImpact").exists());
    send(post("/v1/risk/risks"), manager, risk(ORG_SITE_1, "x", "climate_physical", "ZZ", 2, 2, null, null)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.sectorCode").exists());
    send(post("/v1/risk/risks"), manager, risk(ORG_SITE_1, "x", "weather", null, 2, 6, null, null)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.category").exists()).andExpect(jsonPath("$.errors.impact").exists());
    send(post("/v1/risk/risks"), manager, risk(ORG_SITE_1, "x", "climate_physical", null, 2, 2, null, null).replace("}",
        ",\"ownerUserId\":\"" + roleUserId("AUDITOR") + "\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.ownerUserId").exists());
    send(post("/v1/risk/risks"), manager, risk(ORG_SITE_2, "x", "climate_physical", null, 2, 2, null, null)).andExpect(status().isForbidden());

    JsonNode flood = data(send(post("/v1/risk/risks"), manager, risk(ORG_SITE_1, "Flooding of the tank farm", "climate_physical", "D", 4, 5, 2, 3))
        .andExpect(status().isCreated()));
    assertThat(flood.path("score").asInt()).isEqualTo(20);
    assertThat(flood.path("band").asText()).isEqualTo("very_high");
    assertThat(flood.path("residualScore").asInt()).isEqualTo(6);
    assertThat(flood.path("residualBand").asText()).isEqualTo("medium");
    send(post("/v1/risk/risks"), manager, risk(ORG_SITE_1, "Carbon tax", "climate_transition", "D", 3, 4, null, null)).andExpect(status().isCreated());
    send(post("/v1/risk/risks"), director, risk(ORG_SITE_2, "Community unrest", "social", "B", 2, 2, null, null)).andExpect(status().isCreated());
    send(post("/v1/risk/risks"), director, risk(ORG_HQ, "Board independence", "governance", null, 1, 3, null, null)).andExpect(status().isCreated());

    JsonNode portfolio = read("/v1/risk/heatmap?level=portfolio", director);
    assertThat(portfolio.path("risks").asInt()).isEqualTo(4);
    assertThat(portfolio.path("grid").get(3).get(4).asInt()).as("likelihood 4 × impact 5").isEqualTo(1);
    assertThat(portfolio.path("byBand").path("very_high").asInt()).isEqualTo(1);
    JsonNode residual = read("/v1/risk/heatmap?level=portfolio&residual=true", director);
    assertThat(residual.path("grid").get(3).get(4).asInt()).isZero();
    assertThat(residual.path("grid").get(1).get(2).asInt()).as("after controls: 2 × 3").isEqualTo(1);
    assertThat(residual.path("byBand").path("very_high").asInt()).isZero();

    // Entity level drills down the tree: the top organisation, then its sites (HQ's own risks shown on their own).
    JsonNode top = read("/v1/risk/heatmap?level=entity", director);
    JsonNode hq = top.path("groups").get(0);
    assertThat(hq.path("label").asText()).isEqualTo("A HQ");
    assertThat(hq.path("risks").asInt()).isEqualTo(4);
    assertThat(hq.path("hasChildren").asBoolean()).isTrue();
    JsonNode sites = read("/v1/risk/heatmap?level=entity&orgId=" + ORG_HQ, director);
    Map<String, Integer> perSite = new HashMap<>();
    sites.path("groups").forEach(g -> perSite.put(g.path("label").asText(), g.path("risks").asInt()));
    assertThat(perSite).containsEntry("A HQ (itself)", 1).containsEntry("A Site 1", 2).containsEntry("A Site 2", 1);
    JsonNode sectors = read("/v1/risk/heatmap?level=sector", director);
    assertThat(sectors.path("groups").get(0).path("key").asText()).as("highest score first").isEqualTo("D");
    assertThat(sectors.path("groups").get(0).path("risks").asInt()).isEqualTo(2);
    // The manager (Site 1) sees only Site 1's risks.
    assertThat(read("/v1/risk/heatmap?level=portfolio", manager).path("risks").asInt()).isEqualTo(2);
    mvc.perform(get("/v1/risk/heatmap?level=galaxy").header("Authorization", director)).andExpect(status().isBadRequest());
    // Climate risks in the register evidence IFRS S2 paragraph 10 for the current year.
    assertThat(requirementStatus("ISSB_S2", "S2-10", TODAY.getYear())).isNotEqualTo("missing");
  }

  // ---------------------------------------------------------------- climate scenarios

  @Test
  void scenarioAnalysisFromSourcedCarbonPricesAndSitePhysicalRisk() throws Exception {
    String manager = bearerForRole("ESG_MANAGER");
    String director = bearerForRole("ESG_DIRECTOR");
    JsonNode scenarios = read("/v1/risk/scenarios", director);
    assertThat(scenarios.size()).isEqualTo(5);
    assertThat(scenarios.get(0).path("code").asText()).isEqualTo("NGFS_NZ2050");
    send(put("/v1/risk/scenarios/prices"), manager, "{\"scenarioCode\":\"NOPE\",\"priceYear\":2030,\"priceUsd\":100,\"source\":\"s\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.scenarioCode").exists());
    send(put("/v1/risk/scenarios/prices"), manager, "{\"scenarioCode\":\"NGFS_CP\",\"priceYear\":2019,\"priceUsd\":-1,\"source\":\"\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.priceYear").exists()).andExpect(jsonPath("$.errors.priceUsd").exists())
        .andExpect(jsonPath("$.errors.source").exists());
    send(put("/v1/risk/scenarios/prices"), bearerForRole("AUDITOR"), "{\"scenarioCode\":\"NGFS_CP\",\"priceYear\":2030,\"priceUsd\":5,\"source\":\"s\"}")
        .andExpect(status().isForbidden());
    send(put("/v1/risk/scenarios/prices"), manager, "{\"scenarioCode\":\"NGFS_NZ2050\",\"priceYear\":2030,\"priceUsd\":150,\"source\":\"Test path\"}")
        .andExpect(status().isOk());
    // Setting the same year again replaces the price.
    JsonNode after = data(send(put("/v1/risk/scenarios/prices"), manager, "{\"scenarioCode\":\"NGFS_CP\",\"priceYear\":2030,\"priceUsd\":10,"
        + "\"source\":\"Test path\"}").andExpect(status().isOk()));
    send(put("/v1/risk/scenarios/prices"), manager, "{\"scenarioCode\":\"NGFS_CP\",\"priceYear\":2030,\"priceUsd\":12,\"source\":\"Test path v2\"}")
        .andExpect(status().isOk());
    JsonNode cp = null;
    for (JsonNode s : read("/v1/risk/scenarios", director)) {
      if ("NGFS_CP".equals(s.path("code").asText())) {
        cp = s;
      }
    }
    assertThat(after).isNotNull();
    assertThat(cp.path("prices").size()).isEqualTo(1);
    assertThat(cp.path("prices").get(0).path("priceUsd").decimalValue()).isEqualByComparingTo("12");

    // Transition exposure = price × Scope 1+2 of the baseline year.
    JsonNode t = read("/v1/risk/scenarios/transition?baselineYear=2026", director);
    BigDecimal baseline = t.path("baselineTonnes").decimalValue();
    assertThat(baseline.signum()).isPositive();
    JsonNode nz = null;
    for (JsonNode s : t.path("scenarios")) {
      if ("NGFS_NZ2050".equals(s.path("code").asText())) {
        nz = s;
      }
    }
    JsonNode row = nz.path("rows").get(0);
    assertThat(row.path("year").asInt()).isEqualTo(2030);
    assertThat(row.path("costNoActionUsd").decimalValue()).isEqualByComparingTo(baseline.multiply(new BigDecimal("150")).setScale(2, java.math.RoundingMode.HALF_UP));
    mvc.perform(get("/v1/risk/scenarios/transition").header("Authorization", bearerForRole("BOARD_MEMBER"))).andExpect(status().isForbidden());

    // Physical risk per site and hazard, with its source; the climate data adapter is a labelled sandbox.
    String body = "{\"orgId\":\"" + ORG_SITE_1 + "\",\"hazard\":\"flooding\",\"pathway\":\"RCP8.5\",\"horizonYear\":2050,\"exposure\":4,"
        + "\"vulnerability\":4,\"source\":\"State flood atlas\",\"assessedOn\":\"" + TODAY + "\"}";
    asOwnerInTenantA("update risk.physical_assessments set deleted_at = now() where deleted_at is null and org_id = '" + ORG_SITE_1 + "'");
    send(post("/v1/risk/physical"), manager, body.replace("2050", "2040").replace("\"" + TODAY + "\"", "\"" + TODAY.plusDays(2) + "\""))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.horizonYear").exists()).andExpect(jsonPath("$.errors.assessedOn").exists());
    send(post("/v1/risk/physical"), manager, body.replace("RCP8.5", "RCP6.0").replace("State flood atlas", "")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.pathway").exists()).andExpect(jsonPath("$.errors.source").exists());
    JsonNode p = data(send(post("/v1/risk/physical"), manager, body).andExpect(status().isCreated()));
    assertThat(p.path("score").asInt()).isEqualTo(16);
    assertThat(p.path("band").asText()).isEqualTo("very_high");
    JsonNode suggestion = read("/v1/risk/physical/suggest?orgId=" + ORG_SITE_1 + "&hazard=flooding", manager);
    assertThat(suggestion.path("provider").asText()).isEqualTo("Sandbox");
    assertThat(suggestion.path("status").asText()).isEqualTo("not_connected");
    assertThat(read("/v1/risk/physical/data-source", manager).path("sandbox").asBoolean()).isTrue();
    // Scenario analysis evidences climate resilience (IFRS S2 paragraph 22, TCFD Strategy c).
    assertThat(requirementStatus("ISSB_S2", "S2-22", TODAY.getYear())).isNotEqualTo("missing");
    assertThat(requirementStatus("TCFD", "STR-c", TODAY.getYear())).isNotEqualTo("missing");
  }

  @Test
  void topicsRisksAndAssessmentsCanBeCorrectedAndRemoved() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    JsonNode a = data(send(post("/v1/risk/assessments"), director, assessment("DMA edits " + System.nanoTime(), 2024, "0", GROUPS))
        .andExpect(status().isCreated()));
    String path = "/v1/risk/assessments/" + a.path("id").asText();
    a = data(send(post(path + "/topics"), director, topic("Water", "actual", 3, 3, 3, null, 2, 2, "\"investors\":4")).andExpect(status().isCreated()));
    JsonNode water = byTopic(a).get("Water");
    String tp = path + "/topics/" + water.path("id").asText();
    a = data(send(put(tp), director, topic("Water stress", "potential", 3, 3, 3, 5, 4, 4, "\"communities\":2")
        .replace("}}", "},\"version\":" + water.path("version").asLong() + "}")).andExpect(status().isOk()));
    JsonNode ws = byTopic(a).get("Water stress");
    assertThat(ws.path("position").path("impact").decimalValue()).isEqualByComparingTo("3.00");
    assertThat(ws.path("ratings").path("communities").asInt()).isEqualTo(2);
    assertThat(ws.path("ratings").has("investors")).isFalse();
    // A topic linked from the register can't go until it is unlinked.
    JsonNode r = data(send(post("/v1/risk/risks"), director, risk(ORG_HQ, "Water rationing", "environmental", null, 3, 3, null, null)
        .replace("}", ",\"topicId\":\"" + ws.path("id").asText() + "\"}")).andExpect(status().isCreated()));
    assertThat(r.path("topicName").asText()).isEqualTo("Water stress");
    assertThat(read("/v1/risk/topics", director).toString()).contains("Water stress (2024)");
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path + "/topics/" + ws.path("id").asText()
        + "?version=" + ws.path("version").asLong()).header("Authorization", director)).andExpect(status().isConflict());
    r = data(send(put("/v1/risk/risks/" + r.path("id").asText()), director, risk(ORG_HQ, "Water rationing", "environmental", null, 3, 3, null, null)
        .replace("\"status\":\"open\"", "\"status\":\"closed\",\"reviewDate\":\"2020-01-01\"").replace("}", ",\"version\":" + r.path("version").asLong() + "}"))
        .andExpect(status().isOk()));
    assertThat(r.path("status").asText()).isEqualTo("closed");
    assertThat(r.path("reviewDue").asBoolean()).as("closed risks aren't due").isFalse();
    assertThat(read("/v1/risk/risks?status=closed&category=environmental", director).toString()).contains("Water rationing");
    mvc.perform(get("/v1/risk/risks?status=nope").header("Authorization", director)).andExpect(status().isBadRequest());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/v1/risk/risks/" + r.path("id").asText() + "?version="
        + r.path("version").asLong()).header("Authorization", director)).andExpect(status().isNoContent());
    a = data(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path + "/topics/" + ws.path("id").asText()
        + "?version=" + ws.path("version").asLong()).header("Authorization", director)).andExpect(status().isOk()));
    assertThat(a.path("topics").asInt()).isZero();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path + "?version=" + a.path("version").asLong())
        .header("Authorization", director)).andExpect(status().isNoContent());
    mvc.perform(get(path).header("Authorization", director)).andExpect(status().isNotFound());

    // Physical assessments and carbon prices can be corrected and removed; report variables count the register.
    JsonNode p = data(send(post("/v1/risk/physical"), director, "{\"orgId\":\"" + ORG_SITE_2 + "\",\"hazard\":\"storms\",\"pathway\":\"RCP4.5\","
        + "\"horizonYear\":2030,\"exposure\":2,\"vulnerability\":2,\"source\":\"s\",\"assessedOn\":\"" + TODAY + "\"}").andExpect(status().isCreated()));
    p = data(send(put("/v1/risk/physical/" + p.path("id").asText()), director, "{\"orgId\":\"" + ORG_SITE_2 + "\",\"hazard\":\"storms\",\"pathway\":\"RCP8.5\","
        + "\"horizonYear\":2050,\"exposure\":5,\"vulnerability\":3,\"source\":\"s2\",\"assessedOn\":\"" + TODAY + "\",\"version\":" + p.path("version").asLong() + "}")
        .andExpect(status().isOk()));
    assertThat(p.path("band").asText()).isEqualTo("very_high");
    assertThat(read("/v1/risk/physical?orgId=" + ORG_SITE_2, director).toString()).contains("storms");
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/v1/risk/physical/" + p.path("id").asText() + "?version="
        + p.path("version").asLong()).header("Authorization", director)).andExpect(status().isNoContent());
    send(put("/v1/risk/scenarios/prices"), director, "{\"scenarioCode\":\"NGFS_DT\",\"priceYear\":2040,\"priceUsd\":80,\"source\":\"t\"}").andExpect(status().isOk());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/v1/risk/scenarios/NGFS_DT/prices/2040")
        .header("Authorization", director)).andExpect(status().isOk());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/v1/risk/scenarios/NGFS_DT/prices/2040")
        .header("Authorization", director)).andExpect(status().isNotFound());
    Map<String, VariableValue> v = asTenantA(() -> variables.resolve(Set.of("risk:open_risks", "risk:high_risks", "risk:climate_risks",
        "risk:physical_high_risks", "risk:scenarios_analysed", "risk:material_topics"), LocalDate.of(2019, 1, 1), LocalDate.of(2019, 12, 31), "t"));
    assertThat(v.get("risk:open_risks").status()).isEqualTo("ok");
    assertThat(v.get("risk:high_risks").status()).isEqualTo("ok");
    assertThat(v.get("risk:climate_risks").status()).isEqualTo("ok");
    assertThat(v.get("risk:physical_high_risks").status()).isEqualTo("ok");
    assertThat(v.get("risk:scenarios_analysed").status()).isEqualTo("ok");
    assertThat(v.get("risk:material_topics").status()).as("no approved assessment for 2019").isEqualTo("missing");
    JsonNode o = read("/v1/risk/overview", director);
    assertThat(o.has("risksByBand")).isTrue();
    mvc.perform(get("/v1/risk/assessments?year=2024").header("Authorization", director)).andExpect(status().isOk());
  }

  @Test
  void readersReadWritersWriteAndOtherTenantsCantSeeTheModule() throws Exception {
    mvc.perform(get("/v1/risk/overview").header("Authorization", bearerForRole("AUDITOR"))).andExpect(status().isOk());
    send(post("/v1/risk/risks"), bearerForRole("AUDITOR"), risk(ORG_HQ, "x", "social", null, 2, 2, null, null)).andExpect(status().isForbidden());
    mvc.perform(get("/v1/risk/risks").header("Authorization", bearerForRole("INVESTOR_VIEWER"))).andExpect(status().isForbidden());
    mvc.perform(get("/v1/risk/overview").header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B))).andExpect(status().isForbidden());
    JsonNode dash = read("/v1/dashboard/executive", bearerForRole("ESG_DIRECTOR"));
    assertThat(dash.path("risk").isObject()).isTrue();
    assertThat(dash.path("risk").has("openRisks")).isTrue();
    JsonNode board = read("/v1/dashboard/executive", bearerForRole("BOARD_MEMBER"));
    assertThat(board.path("risk").isMissingNode() || board.path("risk").isNull()).isTrue();
  }
}
