package com.esg.datahub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * WP-20 data point workflow (BRD-5.2.1-02, journey 3): draft → submitted → validated → approved, rejection with a
 * reason, segregation of duties, the database lock on approved data, reopening, history, rules and quality score.
 */
class DataPointWorkflowTest extends ApiTestSupport {

  /** Distinct one-day periods in 2026 so tests never trip the duplicate check against each other. */
  private static final AtomicInteger DAY = new AtomicInteger();

  @Autowired private ObjectMapper json;

  private String collector() {
    return bearerForRole("DATA_COLLECTOR"); // scoped to Site 1
  }

  private String analyst() {
    return bearerForRole("ESG_ANALYST"); // scoped to Site 1, data_points:validate
  }

  private String director() {
    return bearerForRole("ESG_DIRECTOR"); // tenant-wide, validate + approve
  }

  private static String period() {
    String day = LocalDate.of(2026, 7, 1).plusDays(DAY.getAndIncrement()).toString();
    return "\"reportingPeriodStart\":\"" + day + "\",\"reportingPeriodEnd\":\"" + day + "\"";
  }

  private JsonNode data(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
  }

  private JsonNode create(String who, String body) throws Exception {
    return data(mvc.perform(post("/v1/data/points").header("Authorization", who)
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()));
  }

  private JsonNode diesel(String who) throws Exception {
    return create(who, "{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"diesel_litres\",\"numericValue\":420," + period() + "}");
  }

  private ResultActions step(String who, String action, JsonNode point, String reason) throws Exception {
    String body = "{\"version\":" + point.path("version").asLong() + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}";
    return mvc.perform(post("/v1/data/points/" + point.path("id").asText() + "/" + action).header("Authorization", who)
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  void journey3CollectorSubmitsAnalystValidatesDirectorApproves() throws Exception {
    JsonNode draft = diesel(collector());
    assertThat(draft.path("validationStatus").asText()).isEqualTo("draft");
    assertThat(json.convertValue(draft.path("actions"), String[].class)).contains("edit", "submit", "delete");

    JsonNode submitted = data(step(collector(), "submit", draft, null).andExpect(status().isOk()));
    assertThat(submitted.path("validationStatus").asText()).isEqualTo("submitted");
    assertThat(submitted.path("submittedBy").asText()).isEqualTo(roleUserId("DATA_COLLECTOR"));

    // The analyst sees validate/reject; the collector can't validate their own submission.
    String id = draft.path("id").asText();
    mvc.perform(get("/v1/data/points/" + id).header("Authorization", analyst()))
        .andExpect(jsonPath("$.data.actions", contains("validate", "reject")));
    step(collector(), "validate", submitted, null).andExpect(status().isForbidden());
    JsonNode validated = data(step(analyst(), "validate", submitted, null).andExpect(status().isOk()));
    assertThat(validated.path("validatedBy").asText()).isEqualTo(roleUserId("ESG_ANALYST"));

    // Only data_points:approve may approve, and never the validator.
    step(analyst(), "approve", validated, null).andExpect(status().isForbidden());
    JsonNode approved = data(step(director(), "approve", validated, null).andExpect(status().isOk()));
    assertThat(approved.path("validationStatus").asText()).isEqualTo("approved");
    assertThat(approved.path("approvedBy").asText()).isEqualTo(roleUserId("ESG_DIRECTOR"));
    assertThat(json.convertValue(approved.path("actions"), String[].class)).containsExactly("reopen");

    // Approval workflow rows (TRD §4.3 approval_workflows / approval_steps) record both steps.
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      var rs = st.executeQuery("select w.status, count(s.id) from data_hub.approval_workflows w "
          + "join data_hub.approval_steps s on s.workflow_id = w.id where w.data_point_id = '" + id + "' group by w.status");
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("approved");
      assertThat(rs.getInt(2)).isEqualTo(2);
    }

    // History (journey 29): who did what, in order, from the audit trail.
    mvc.perform(get("/v1/data/points/" + id + "/history").header("Authorization", bearerForRole("AUDITOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].action", contains("created", "submitted", "validated", "approved")))
        .andExpect(jsonPath("$.data[3].userId").value(roleUserId("ESG_DIRECTOR")))
        .andExpect(jsonPath("$.data[3].userName").value("Esg Director Fixture"));
  }

  @Test
  void approvedDataIsLockedInTheDatabaseAndReopeningNeedsAReason() throws Exception {
    JsonNode p = diesel(collector());
    p = data(step(collector(), "submit", p, null));
    p = data(step(analyst(), "validate", p, null));
    p = data(step(director(), "approve", p, null));
    String id = p.path("id").asText();

    // Neither the API nor direct SQL can change an approved value.
    mvc.perform(put("/v1/data/points/" + id).header("Authorization", director()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"diesel_litres\",\"numericValue\":1," + period()
                + ",\"version\":" + p.path("version").asLong() + "}"))
        .andExpect(status().isConflict());
    assertThatThrownBy(() -> asOwnerInTenantA("update data_hub.data_points set numeric_value = 1 where id = '" + id + "'"))
        .isInstanceOf(SQLException.class).hasMessageContaining("locked");
    assertThatThrownBy(() -> asOwnerInTenantA("update data_hub.data_points set validation_status = 'draft' where id = '" + id + "'"))
        .isInstanceOf(SQLException.class).hasMessageContaining("reason");

    step(director(), "reopen", p, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
    step(analyst(), "reopen", p, "typo").andExpect(status().isForbidden());
    JsonNode reopened = data(step(director(), "reopen", p, "Invoice corrected by supplier").andExpect(status().isOk()));
    assertThat(reopened.path("validationStatus").asText()).isEqualTo("draft");
    assertThat(reopened.path("statusReason").asText()).isEqualTo("Invoice corrected by supplier");
    mvc.perform(get("/v1/data/points/" + id + "/history").header("Authorization", director()))
        .andExpect(jsonPath("$.data[-1].action").value("reopened"))
        .andExpect(jsonPath("$.data[-1].reason").value("Invoice corrected by supplier"));
  }

  @Test
  void rejectionNeedsAReasonAndReturnsThePointForCorrection() throws Exception {
    JsonNode p = data(step(collector(), "submit", diesel(collector()), null));
    step(analyst(), "reject", p, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
    JsonNode rejected = data(step(analyst(), "reject", p, "Meter photo missing").andExpect(status().isOk()));
    assertThat(rejected.path("validationStatus").asText()).isEqualTo("rejected");
    assertThat(rejected.path("statusReason").asText()).isEqualTo("Meter photo missing");

    // The collector corrects it (back to draft) and resubmits.
    String id = rejected.path("id").asText();
    JsonNode edited = data(mvc.perform(put("/v1/data/points/" + id).header("Authorization", collector())
            .contentType(MediaType.APPLICATION_JSON).content("{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"diesel_litres\","
                + "\"numericValue\":430," + period() + ",\"version\":" + rejected.path("version").asLong() + "}"))
        .andExpect(status().isOk()));
    assertThat(edited.path("validationStatus").asText()).isEqualTo("draft");
    assertThat(data(step(collector(), "submit", edited, null)).path("validationStatus").asText()).isEqualTo("submitted");
  }

  @Test
  void stateVersionAndDeletionRules() throws Exception {
    JsonNode p = diesel(collector());
    step(analyst(), "validate", p, null).andExpect(status().isConflict()); // a draft can't be validated
    mvc.perform(post("/v1/data/points/" + p.path("id").asText() + "/submit").header("Authorization", collector())
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":99}"))
        .andExpect(status().isConflict());
    mvc.perform(post("/v1/data/points/" + p.path("id").asText() + "/submit").header("Authorization", collector())
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.version").exists());

    // Someone else's draft: analysts have no data_points:delete.
    mvc.perform(delete("/v1/data/points/" + p.path("id").asText() + "?version=0").header("Authorization", analyst()))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/v1/data/points/" + p.path("id").asText() + "?version=0").header("Authorization", collector()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/v1/data/points/" + p.path("id").asText()).header("Authorization", collector()))
        .andExpect(status().isNotFound());

    JsonNode submitted = data(step(collector(), "submit", diesel(collector()), null));
    mvc.perform(delete("/v1/data/points/" + submitted.path("id").asText() + "?version=" + submitted.path("version").asLong())
            .header("Authorization", collector()))
        .andExpect(status().isConflict());
  }

  @Test
  void duplicatesAreRefusedOverlapsAndCrossMetricInconsistenciesAreWarnings() throws Exception {
    String p = period();
    create(director(), "{\"orgId\":\"" + ORG_HQ + "\",\"metricKey\":\"employees_total\",\"numericValue\":50," + p + "}");
    mvc.perform(post("/v1/data/points").header("Authorization", director()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"orgId\":\"" + ORG_HQ + "\",\"metricKey\":\"employees_total\",\"numericValue\":51," + p + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.reportingPeriodStart").value(org.hamcrest.Matchers.containsString("already exists")));

    // 60 female employees out of 50 in total: saved, with a warning, and accuracy loses the failed check.
    JsonNode female = create(director(), "{\"orgId\":\"" + ORG_HQ + "\",\"metricKey\":\"employees_female\",\"numericValue\":60," + p + "}");
    assertThat(female.path("validationWarnings").findValuesAsText("code")).contains("female_lte_total");
    assertThat(female.path("qualityBreakdown").path("accuracy").decimalValue()).isZero();

    // A quarter overlapping the HQ month warns about double counting.
    JsonNode month = create(director(), "{\"orgId\":\"" + ORG_HQ + "\",\"metricKey\":\"lpg_kg\",\"numericValue\":5,"
        + "\"reportingPeriodStart\":\"2026-11-01\",\"reportingPeriodEnd\":\"2026-11-30\"}");
    JsonNode quarter = create(director(), "{\"orgId\":\"" + ORG_HQ + "\",\"metricKey\":\"lpg_kg\",\"numericValue\":15,"
        + "\"reportingPeriodStart\":\"2026-10-01\",\"reportingPeriodEnd\":\"2026-12-31\"}");
    assertThat(month.path("validationWarnings").findValuesAsText("code")).doesNotContain("period_overlap");
    assertThat(quarter.path("validationWarnings").findValuesAsText("code")).contains("period_overlap", "period_not_ended");
  }

  @Test
  void rangeRulesFromTheCatalogue() throws Exception {
    mvc.perform(post("/v1/data/points").header("Authorization", director()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"orgId\":\"" + ORG_HQ + "\",\"metricKey\":\"board_size\",\"numericValue\":0," + period() + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.numericValue").value("A board has at least one member"));
    JsonNode big = create(director(), "{\"orgId\":\"" + ORG_HQ + "\",\"metricKey\":\"board_size\",\"numericValue\":45," + period() + "}");
    assertThat(big.path("validationWarnings").findValuesAsText("code")).contains("board_size_plausible");
    mvc.perform(get("/v1/data/rules?metricKey=board_size").header("Authorization", collector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].code", hasItem("board_size_minimum")));
  }

  @Test
  void currencyMetricsConvertWithTheDatedRateOrRefuse() throws Exception {
    // Fixture: USD→NGN 1500 dated 2026-01-01 (test value). Base currency of tenant A is NGN.
    JsonNode usd = create(director(), "{\"orgId\":\"" + ORG_HQ + "\",\"metricKey\":\"community_investment\",\"numericValue\":10,"
        + "\"currency\":\"USD\"," + period() + "}");
    assertThat(usd.path("numericValue").decimalValue()).isEqualByComparingTo("15000");
    assertThat(usd.path("unit").asText()).isEqualTo("NGN");
    assertThat(usd.path("originalAmount").decimalValue()).isEqualByComparingTo("10");
    assertThat(usd.path("originalCurrency").asText()).isEqualTo("USD");
    assertThat(usd.path("fxRateDate").asText()).isEqualTo("2026-01-01");
    assertThat(usd.path("fxRateSource").asText()).isEqualTo("Test fixture rate (not a real quote)");

    JsonNode ngn = create(director(), "{\"orgId\":\"" + ORG_HQ + "\",\"metricKey\":\"community_investment\",\"numericValue\":10,"
        + period() + "}");
    assertThat(ngn.path("originalCurrency").isMissingNode() || ngn.path("originalCurrency").isNull()).as("no conversion").isTrue();
    assertThat(ngn.path("numericValue").decimalValue()).isEqualByComparingTo("10");

    mvc.perform(post("/v1/data/points").header("Authorization", director()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"orgId\":\"" + ORG_HQ + "\",\"metricKey\":\"community_investment\",\"numericValue\":10,"
                + "\"currency\":\"EUR\"," + period() + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.currency").value(org.hamcrest.Matchers.containsString("No EUR→NGN exchange rate")));
    // A rate dated after the period doesn't apply.
    mvc.perform(post("/v1/data/points").header("Authorization", director()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"orgId\":\"" + ORG_HQ + "\",\"metricKey\":\"community_investment\",\"numericValue\":10,"
                + "\"currency\":\"USD\",\"reportingPeriodStart\":\"2025-12-01\",\"reportingPeriodEnd\":\"2025-12-31\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.currency").exists());
  }

  @Test
  void qualityScoreBreakdownIsStoredAndEvidenceAndSourceRaiseCompleteness() throws Exception {
    JsonNode bare = diesel(collector());
    assertThat(bare.path("qualityBreakdown").path("completeness").decimalValue()).isEqualByComparingTo("0.3333");
    JsonNode full = create(collector(), "{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"diesel_litres\",\"numericValue\":420,"
        + "\"sourceId\":\"a0000000-0000-4000-8000-0000000000c1\",\"evidenceFileIds\":[\"a0000000-0000-4000-8000-0000000004f1\"],"
        + period() + "}");
    assertThat(full.path("qualityBreakdown").path("completeness").decimalValue()).isEqualByComparingTo("1");
    assertThat(full.path("qualityScore").decimalValue()).isGreaterThan(bare.path("qualityScore").decimalValue());
    assertThat(full.path("sourceName").asText()).isEqualTo("Manual");
  }

  @Test
  void listFiltersByStatusMetricAndPeriodAndRefusesUnknownStatus() throws Exception {
    JsonNode p = diesel(collector());
    String day = p.path("reportingPeriodStart").asText();
    mvc.perform(get("/v1/data/points?status=draft&metricKey=diesel_litres&from=" + day + "&to=" + day)
            .header("Authorization", collector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[*].id", hasItem(p.path("id").asText())));
    mvc.perform(get("/v1/data/points?status=approved&from=" + day + "&to=" + day).header("Authorization", collector()))
        .andExpect(jsonPath("$.data.content[*].id", not(hasItem(p.path("id").asText()))));
    mvc.perform(get("/v1/data/points?status=pending").header("Authorization", collector()))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.status").exists());
  }

  @Test
  void orgScopedValidatorCantTouchOtherOrganisations() throws Exception {
    JsonNode hq = data(step(director(), "submit",
        create(director(), "{\"orgId\":\"" + ORG_HQ + "\",\"metricKey\":\"lpg_kg\",\"numericValue\":3," + period() + "}"), null));
    step(analyst(), "validate", hq, null).andExpect(status().isNotFound());
  }
}
