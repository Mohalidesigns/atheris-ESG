package com.esg.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.notifications.NotificationService;
import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * WP-22 Regulatory Compliance Manager through the API: gap analysis for every framework with content, journey 10
 * (entering data closes a gap), journey 11 (GRI data maps to ISSB), attestation rules, filing deadlines with
 * time-travel reminders (journey 12, fixed Clock), the regulatory change workflow and permissions.
 * Each test uses its own reporting year so data from one never closes another's gaps.
 */
class ComplianceApiTest extends ApiTestSupport {

  private static final String EVIDENCE = "a0000000-0000-4000-8000-0000000004f1"; // fixture file

  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private NotificationService notifications;
  @Autowired private PlatformTransactionManager txManager;

  private String officer() {
    return bearerForRole("COMPLIANCE_OFFICER");
  }

  private JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  private ResultActions send(String method, String url, String token, String body) throws Exception {
    var builder = switch (method) {
      case "POST" -> post(url);
      case "PUT" -> put(url);
      default -> throw new IllegalArgumentException(method);
    };
    return mvc.perform(builder.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode gaps(String framework, int year) throws Exception {
    return data(mvc.perform(get("/v1/compliance/gaps?framework=" + framework + "&year=" + year).header("Authorization", officer()))
        .andExpect(status().isOk()));
  }

  private static JsonNode requirement(JsonNode report, String code) {
    for (JsonNode r : report.path("requirements")) {
      if (code.equals(r.path("code").asText())) {
        return r;
      }
    }
    throw new AssertionError(code + " not in report");
  }

  /** collector creates and submits; analyst validates; director approves (Data Hub segregation of duties). */
  private String dataPoint(String metric, String value, String day, boolean approve) throws Exception {
    String collector = bearerForRole("DATA_COLLECTOR");
    JsonNode dp = data(send("POST", "/v1/data/points", collector, "{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"" + metric
        + "\",\"numericValue\":" + value + ",\"reportingPeriodStart\":\"" + day + "\",\"reportingPeriodEnd\":\"" + day + "\"}")
        .andExpect(status().isCreated()));
    String id = dp.path("id").asText();
    dp = data(send("PUT", "/v1/data/points/" + id + "/submit", collector, "{\"version\":" + dp.path("version").asLong() + "}"));
    if (approve) {
      dp = data(send("PUT", "/v1/data/points/" + id + "/validate", bearerForRole("ESG_ANALYST"), "{\"version\":" + dp.path("version").asLong() + "}"));
      send("PUT", "/v1/data/points/" + id + "/approve", bearerForRole("ESG_DIRECTOR"), "{\"version\":" + dp.path("version").asLong() + "}")
          .andExpect(status().isOk());
    }
    return id;
  }

  // ---------------------------------------------------------------- gap analysis per framework

  @Test
  void gapAnalysisForEveryFrameworkWithContent() throws Exception {
    for (String code : List.of("GRI", "ISSB_S1", "ISSB_S2", "TCFD", "CBN_NSBP")) {
      JsonNode r = gaps(code, 2041);
      int leaves = 0;
      for (JsonNode q : r.path("requirements")) {
        if (q.path("leaf").asBoolean()) {
          leaves++;
          assertThat(q.path("status").asText()).as(code + " " + q.path("code").asText()).isEqualTo("missing");
          assertThat(q.path("priority").asText()).as(code + " priority").isIn("high", "medium", "low");
          assertThat(q.path("recommendation").asText()).isNotBlank();
        }
      }
      assertThat(r.path("totalRequirements").asInt()).as(code).isEqualTo(leaves).isPositive();
      assertThat(r.path("missing").asInt()).isEqualTo(leaves);
      assertThat(r.path("overallCompleteness").decimalValue()).isEqualByComparingTo("0");
    }
    // CBN NSBP: 9 principles, mandatory narrative → 40 + 30 = 70 (high).
    JsonNode cbn = gaps("CBN_NSBP", 2041);
    assertThat(cbn.path("totalRequirements").asInt()).isEqualTo(9);
    assertThat(requirement(cbn, "P1").path("priorityScore").asInt()).isEqualTo(70);
    assertThat(cbn.path("highPriority").asInt()).isEqualTo(9);
    // A framework without content (no NAICOM instrument found, D28) has nothing to analyse — and says so.
    JsonNode naicom = gaps("NAICOM", 2041);
    assertThat(naicom.path("totalRequirements").asInt()).isZero();
    assertThat(naicom.path("contentStatus").asText()).isEqualTo("pending_sme");
    // Phase 3 (5): bundled packs give the local frameworks content, still pending SME verification.
    JsonNode sec = gaps("SEC_NG", 2041);
    assertThat(sec.path("totalRequirements").asInt()).isPositive();
    assertThat(sec.path("contentStatus").asText()).isEqualTo("pending_sme");
    mvc.perform(get("/v1/compliance/gaps?framework=NOPE&year=2041").header("Authorization", officer())).andExpect(status().isNotFound());
    mvc.perform(get("/v1/compliance/gaps?framework=GRI&year=1999").header("Authorization", officer()))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.year").exists());
  }

  @Test
  void aReductionTargetEvidencesIfrsS2Paragraph33AndGri305Dash5() throws Exception {
    // F7: the climate-targets requirements see Carbon's reduction targets as supporting data.
    int year = 2071;
    asOwnerInTenantA("update carbon.reduction_targets set deleted_at = now() where deleted_at is null and base_year <= " + year
        + " and target_year >= " + year);
    JsonNode before = requirement(gaps("ISSB_S2", year), "S2-33");
    assertThat(before.path("status").asText()).isEqualTo("missing");
    assertThat(before.path("metricKeys").toString()).contains("source:carbon.reduction_targets");
    JsonNode griBefore = requirement(gaps("GRI", year), "305-5");
    assertThat(griBefore.path("missingMetrics").toString()).contains("source:carbon.reduction_targets");
    assertThat(griBefore.path("recommendation").asText()).contains("Set up a GHG reduction target (Carbon → Reduction targets)");

    send("POST", "/v1/carbon/targets", bearerForRole("ESG_DIRECTOR"), "{\"targetName\":\"Compliance target " + year + "\","
        + "\"targetType\":\"absolute\",\"scope\":\"scope_1_2\",\"baseYear\":" + year + ",\"baseValue\":100,\"targetYear\":" + (year + 5)
        + ",\"targetValue\":50}").andExpect(status().isCreated());
    JsonNode after = requirement(gaps("ISSB_S2", year), "S2-33");
    assertThat(after.path("status").asText()).isEqualTo("partial"); // narrative: supporting data; an attestation completes it
    assertThat(after.path("basis").asText()).isEqualTo("supporting_data");
    JsonNode gri = requirement(gaps("GRI", year), "305-5");
    assertThat(gri.path("missingMetrics").toString()).doesNotContain("source:carbon.reduction_targets");
  }

  @Test
  void frameworkCountsMatchTheGapAnalysis() throws Exception {
    // F8: IFRS S2 has 23 rows of which 5 are section headings; the gap analysis assesses the other 18.
    JsonNode frameworks = data(mvc.perform(get("/v1/compliance/frameworks").header("Authorization", officer())));
    for (JsonNode f : frameworks) {
      if (f.path("requirementCount").asInt() == 0) {
        continue;
      }
      assertThat(f.path("assessableCount").asInt() + f.path("headingCount").asInt()).isEqualTo(f.path("requirementCount").asInt());
      JsonNode report = gaps(f.path("code").asText(), 2026);
      assertThat(report.path("totalRequirements").asInt()).as(f.path("code").asText()).isEqualTo(f.path("assessableCount").asInt());
      if ("ISSB_S2".equals(f.path("code").asText())) {
        assertThat(f.path("assessableCount").asInt()).isEqualTo(18);
        assertThat(f.path("headingCount").asInt()).isEqualTo(5);
      }
    }
  }

  @Test
  void journey10EnteringDataClosesTheGap() throws Exception {
    assertThat(requirement(gaps("GRI", 2042), "303-3").path("status").asText()).isEqualTo("missing");
    // Submitted but not approved: partial, 0% — data in the workflow doesn't count yet.
    String id = dataPoint("water_withdrawal_m3", "1200", "2042-03-31", false);
    JsonNode partial = requirement(gaps("GRI", 2042), "303-3");
    assertThat(partial.path("status").asText()).isEqualTo("partial");
    assertThat(partial.path("completeness").decimalValue()).isEqualByComparingTo("0");
    // Validated and approved: the gap is closed.
    String collectorlessId = dataPoint("water_withdrawal_m3", "800", "2042-06-30", true);
    JsonNode report = gaps("GRI", 2042);
    JsonNode met = requirement(report, "303-3");
    assertThat(met.path("status").asText()).isEqualTo("met");
    assertThat(met.path("basis").asText()).isEqualTo("data");
    assertThat(absent(met.path("priority"))).isTrue();
    assertThat(report.path("met").asInt()).isGreaterThanOrEqualTo(1);
    assertThat(id).isNotEqualTo(collectorlessId);
    // Another tenant's data never closes this tenant's gaps.
    JsonNode b = data(mvc.perform(get("/v1/compliance/gaps?framework=GRI&year=2042")
        .header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B))));
    assertThat(requirement(b, "303-3").path("status").asText()).isEqualTo("missing");
  }

  @Test
  void journey11GriDataMapsToIssbAndAttestationsCarryAcrossEquivalents() throws Exception {
    // Scope 1 emissions recorded in Carbon for 2043 → the shared metric scope1_tco2e is met.
    send("POST", "/v1/carbon/emissions", bearerForRole("ESG_DIRECTOR"), "{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\","
        + "\"sourceActivity\":\"diesel_generator\",\"activityData\":100,\"activityUnit\":\"litre\","
        + "\"reportingPeriodStart\":\"2043-02-01\",\"reportingPeriodEnd\":\"2043-02-28\"}").andExpect(status().isCreated());
    JsonNode issb = gaps("ISSB_S2", 2043);
    assertThat(requirement(issb, "S2-29(a)(i)(1)").path("status").asText()).isEqualTo("met");
    // GRI 305-1 also lists fuel metrics: 1 of 5 approved → partial 20%, until someone attests the rest don't apply.
    JsonNode gri = requirement(gaps("GRI", 2043), "305-1");
    assertThat(gri.path("status").asText()).isEqualTo("partial");
    assertThat(gri.path("completeness").decimalValue()).isEqualByComparingTo("20.0");
    send("PUT", "/v1/compliance/attestations", officer(), "{\"framework\":\"GRI\",\"requirementCode\":\"305-1\",\"year\":2043,"
        + "\"status\":\"complete\",\"notes\":\"Diesel only; no petrol, LPG or gas use\",\"evidenceFileIds\":[\"" + EVIDENCE + "\"]}")
        .andExpect(status().isOk());
    assertThat(requirement(gaps("GRI", 2043), "305-1").path("status").asText()).isEqualTo("met");

    JsonNode map = data(send("POST", "/v1/compliance/map", officer(),
        "{\"sourceFramework\":\"GRI\",\"targetFramework\":\"ISSB_S2\",\"year\":2043}").andExpect(status().isOk()));
    JsonNode scope1 = null;
    for (JsonNode r : map.path("requirements")) {
      if ("S2-29(a)(i)(1)".equals(r.path("code").asText())) {
        scope1 = r;
      }
    }
    assertThat(scope1).isNotNull();
    assertThat(scope1.path("status").asText()).isEqualTo("met");
    assertThat(scope1.path("sources").get(0).path("code").asText()).isEqualTo("305-1");
    assertThat(scope1.path("sources").get(0).path("relation").asText()).isEqualTo("equivalent");
    assertThat(map.path("metViaSource").asInt()).isGreaterThanOrEqualTo(1);

    // A narrative attested once in TCFD meets its ISSB S2 equivalent: enter once, map to many.
    send("PUT", "/v1/compliance/attestations", officer(), "{\"framework\":\"TCFD\",\"requirementCode\":\"GOV-a\",\"year\":2043,"
        + "\"status\":\"complete\",\"evidenceFileIds\":[\"" + EVIDENCE + "\"]}").andExpect(status().isOk());
    JsonNode s26a = requirement(gaps("ISSB_S2", 2043), "S2-6(a)");
    assertThat(s26a.path("status").asText()).isEqualTo("met");
    assertThat(s26a.path("basis").asText()).isEqualTo("mapped");
    assertThat(s26a.path("satisfiedBy").asText()).isEqualTo("TCFD GOV-a");
    mvc.perform(post("/v1/compliance/map").header("Authorization", officer()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"sourceFramework\":\"GRI\",\"targetFramework\":\"GRI\",\"year\":2043}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.targetFramework").exists());
  }

  @Test
  void attestationRules() throws Exception {
    String base = "{\"framework\":\"CBN_NSBP\",\"requirementCode\":\"P2\",\"year\":2044,";
    send("PUT", "/v1/compliance/attestations", officer(), base + "\"status\":\"complete\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.evidenceFileIds").exists());
    send("PUT", "/v1/compliance/attestations", officer(), base + "\"status\":\"not_applicable\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.notes").exists());
    send("PUT", "/v1/compliance/attestations", officer(), base + "\"status\":\"complete\",\"evidenceFileIds\":[\"a0000000-0000-4000-8000-000000000000\"]}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.evidenceFileIds").value("contains a file that doesn't exist"));
    send("PUT", "/v1/compliance/attestations", officer(), base + "\"status\":\"done\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.status").exists());
    // Quantitative requirements: no data → can't be attested complete; progress comes from data.
    send("PUT", "/v1/compliance/attestations", officer(), "{\"framework\":\"GRI\",\"requirementCode\":\"303-3\",\"year\":2044,"
        + "\"status\":\"complete\",\"evidenceFileIds\":[\"" + EVIDENCE + "\"]}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.status").exists());
    send("PUT", "/v1/compliance/attestations", officer(), "{\"framework\":\"GRI\",\"requirementCode\":\"303-3\",\"year\":2044,"
        + "\"status\":\"in_progress\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.status").exists());
    // Headings aren't attested.
    send("PUT", "/v1/compliance/attestations", officer(), "{\"framework\":\"GRI\",\"requirementCode\":\"GRI 2\",\"year\":2044,"
        + "\"status\":\"not_applicable\",\"notes\":\"x\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.requirementCode").exists());
    send("PUT", "/v1/compliance/attestations", officer(), "{\"framework\":\"GRI\",\"requirementCode\":\"999-9\",\"year\":2044,"
        + "\"status\":\"not_applicable\",\"notes\":\"x\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.requirementCode").exists());
    // Read-only roles can't attest.
    send("PUT", "/v1/compliance/attestations", bearerForRole("AUDITOR"), base + "\"status\":\"in_progress\"}").andExpect(status().isForbidden());

    // Record, then update with the version; a stale version is refused.
    JsonNode report = data(send("PUT", "/v1/compliance/attestations", officer(), base + "\"status\":\"in_progress\",\"notes\":\"Drafting\"}")
        .andExpect(status().isOk()));
    JsonNode p2 = requirement(report, "P2");
    assertThat(p2.path("status").asText()).isEqualTo("partial");
    assertThat(p2.path("attestation").path("status").asText()).isEqualTo("in_progress");
    assertThat(p2.path("attestation").path("attestedBy").asText()).isEqualTo("Compliance Officer Fixture");
    long version = p2.path("attestation").path("version").asLong();
    send("PUT", "/v1/compliance/attestations", officer(), base + "\"status\":\"not_applicable\",\"notes\":\"Not a lender\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.version").exists());
    report = data(send("PUT", "/v1/compliance/attestations", officer(), base + "\"status\":\"complete\",\"notes\":\"Board-approved policy\","
        + "\"evidenceFileIds\":[\"" + EVIDENCE + "\"],\"version\":" + version + "}").andExpect(status().isOk()));
    assertThat(requirement(report, "P2").path("status").asText()).isEqualTo("met");
    assertThat(requirement(report, "P2").path("attestation").path("evidenceFileIds").get(0).asText()).isEqualTo(EVIDENCE);
    // Every attestation field reloads from the gap report.
    JsonNode reloaded = requirement(gaps("CBN_NSBP", 2044), "P2").path("attestation");
    assertThat(reloaded.path("status").asText()).isEqualTo("complete");
    assertThat(reloaded.path("notes").asText()).isEqualTo("Board-approved policy");
    assertThat(reloaded.path("attestedAt").asText()).isNotBlank();
    send("PUT", "/v1/compliance/attestations", officer(), base + "\"status\":\"in_progress\",\"version\":" + version + "}")
        .andExpect(status().isConflict());
    // Tenant B sees nothing of it.
    JsonNode b = data(mvc.perform(get("/v1/compliance/gaps?framework=CBN_NSBP&year=2044")
        .header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B))));
    assertThat(requirement(b, "P2").path("status").asText()).isEqualTo("missing");
    assertThat(absent(requirement(b, "P2").path("attestation"))).isTrue();
  }

  @Test
  void statusPerFrameworkAndActiveFrameworks() throws Exception {
    send("PUT", "/v1/compliance/frameworks/active", officer(), "{\"codes\":[\"CBN_NSBP\",\"NOPE\"]}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.codes").exists());
    send("PUT", "/v1/compliance/frameworks/active", bearerForRole("ESG_MANAGER"), "{\"codes\":[\"CBN_NSBP\"]}").andExpect(status().isForbidden());
    JsonNode settings = data(mvc.perform(get("/v1/settings").header("Authorization", bearerForRole("TENANT_ADMIN"))));
    List<String> previous = new ArrayList<>();
    settings.path("defaultFrameworkCodes").forEach(c -> previous.add(c.asText()));
    try {
      send("PUT", "/v1/compliance/frameworks/active", officer(), "{\"codes\":[\"CBN_NSBP\",\"NAICOM\"]}").andExpect(status().isOk());
      JsonNode list = data(mvc.perform(get("/v1/compliance/status?year=2045").header("Authorization", officer())).andExpect(status().isOk()));
      // Active first (incl. NAICOM, shown though it has no content yet), then the other frameworks with content.
      assertThat(list.get(0).path("active").asBoolean()).isTrue();
      List<String> active = new ArrayList<>();
      List<String> all = new ArrayList<>();
      list.forEach(f -> {
        all.add(f.path("code").asText());
        if (f.path("active").asBoolean()) {
          active.add(f.path("code").asText());
        }
      });
      assertThat(active).containsExactlyInAnyOrder("CBN_NSBP", "NAICOM");
      // Phase 3 (5): NGX, SEC NG and the other bundled packs now have content, so they're listed too.
      assertThat(all).contains("GRI", "ISSB_S2", "TCFD", "NGX", "SEC_NG", "PENCOM", "CDP", "GHG_PROTOCOL", "SASB");
    } finally {
      send("PUT", "/v1/compliance/frameworks/active", officer(), json.writeValueAsString(java.util.Map.of("codes", previous)));
    }
  }

  // ---------------------------------------------------------------- filing deadlines

  private String deadlineBody(String date, String extra) {
    return "{\"framework\":\"CBN_NSBP\",\"description\":\"CBN NSBP annual report\",\"deadlineDate\":\"" + date + "\",\"reportingYear\":2049,"
        + "\"ownerUserId\":\"" + roleUserId("COMPLIANCE_OFFICER") + "\",\"reminderDays\":[90,60,30,14,7,1]" + extra + "}";
  }

  @Test
  void deadlineValidationAndFilingTracker() throws Exception {
    send("POST", "/v1/compliance/deadlines", officer(), "{\"description\":\"\",\"reminderDays\":[0,400]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.description").exists())
        .andExpect(jsonPath("$.errors.deadlineDate").exists())
        .andExpect(jsonPath("$.errors.ownerUserId").exists())
        .andExpect(jsonPath("$.errors['reminderDays[0]']").exists())
        .andExpect(jsonPath("$.errors['reminderDays[1]']").exists());
    send("POST", "/v1/compliance/deadlines", officer(), deadlineBody("2049-03-31", "").replace("[90,60,30,14,7,1]", "[7,7]"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reminderDays").exists());
    // The owner must be able to update compliance: an auditor can't own a filing.
    send("POST", "/v1/compliance/deadlines", officer(), deadlineBody("2049-03-31", "").replace(roleUserId("COMPLIANCE_OFFICER"), roleUserId("AUDITOR")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.ownerUserId").exists());
    send("POST", "/v1/compliance/deadlines", officer(), deadlineBody("2049-03-31", "").replace("CBN_NSBP", "NOPE"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.framework").exists());
    send("POST", "/v1/compliance/deadlines", bearerForRole("AUDITOR"), deadlineBody("2049-03-31", "")).andExpect(status().isForbidden());

    JsonNode d = data(send("POST", "/v1/compliance/deadlines", officer(), deadlineBody("2049-03-31", ",\"notes\":\"Board pack first\""))
        .andExpect(status().isCreated()));
    String id = d.path("id").asText();
    assertThat(d.path("status").asText()).isEqualTo("upcoming");
    assertThat(d.path("ownerName").asText()).isEqualTo("Compliance Officer Fixture");
    assertThat(d.path("frameworkName").asText()).isEqualTo("CBN Nigerian Sustainable Banking Principles");

    // Submit: future date refused; then submitted with reference and evidence.
    send("PUT", "/v1/compliance/deadlines/" + id + "/submit", officer(), "{\"version\":" + d.path("version").asLong()
        + ",\"submittedOn\":\"2999-01-01\",\"submissionRef\":\"CBN/2049/1\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.submittedOn").exists());
    d = data(send("PUT", "/v1/compliance/deadlines/" + id + "/submit", officer(), "{\"version\":" + d.path("version").asLong()
        + ",\"submittedOn\":\"2026-01-15\",\"submissionRef\":\"CBN/2049/1\",\"evidenceFileIds\":[\"" + EVIDENCE + "\"]}").andExpect(status().isOk()));
    assertThat(d.path("status").asText()).isEqualTo("submitted");
    assertThat(d.path("submittedOn").asText()).isEqualTo("2026-01-15");
    assertThat(d.path("submissionRef").asText()).isEqualTo("CBN/2049/1");
    send("PUT", "/v1/compliance/deadlines/" + id + "/waive", officer(), "{\"version\":" + d.path("version").asLong() + ",\"reason\":\"x\"}")
        .andExpect(status().isConflict());
    d = data(send("PUT", "/v1/compliance/deadlines/" + id + "/reopen", officer(), "{\"version\":" + d.path("version").asLong() + "}")
        .andExpect(status().isOk()));
    assertThat(d.path("status").asText()).isEqualTo("upcoming");
    assertThat(absent(d.path("submissionRef"))).isTrue();
    // Waiving needs compliance:approve and a reason.
    send("PUT", "/v1/compliance/deadlines/" + id + "/waive", bearerForRole("ESG_DIRECTOR"), "{\"version\":" + d.path("version").asLong() + ",\"reason\":\"\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
    d = data(send("PUT", "/v1/compliance/deadlines/" + id + "/waive", officer(), "{\"version\":" + d.path("version").asLong()
        + ",\"reason\":\"Exempted by CBN letter\"}").andExpect(status().isOk()));
    assertThat(d.path("status").asText()).isEqualTo("waived");
    // Stale version on edit; list filters; delete.
    send("PUT", "/v1/compliance/deadlines/" + id, officer(), deadlineBody("2049-04-30", ",\"version\":0")).andExpect(status().isConflict());
    JsonNode waived = data(mvc.perform(get("/v1/compliance/deadlines?status=waived&from=2049-01-01&to=2049-12-31").header("Authorization", officer())));
    assertThat(waived.findValuesAsText("id")).contains(id);
    mvc.perform(get("/v1/compliance/deadlines?status=late").header("Authorization", officer())).andExpect(status().isBadRequest());
    mvc.perform(delete("/v1/compliance/deadlines/" + id + "?version=" + d.path("version").asLong()).header("Authorization", officer()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/v1/compliance/deadlines/" + id).header("Authorization", officer())).andExpect(status().isNotFound());
    mvc.perform(get("/v1/compliance/deadlines/" + id).header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B)))
        .andExpect(status().isNotFound());
  }

  /** Journey 12 / TRD-11.2.3-03: walk the calendar day by day with a fixed clock; each interval fires exactly once. */
  @Test
  void journey12RemindersFireAt90_60_30_14_7_1DaysThenOverdue() throws Exception {
    JsonNode d = data(send("POST", "/v1/compliance/deadlines", officer(), deadlineBody("2050-06-30", "")).andExpect(status().isCreated()));
    String id = d.path("id").asText();
    String owner = roleUserId("COMPLIANCE_OFFICER");
    long before = inbox(owner, "DEADLINE_REMINDER");
    List<String> firedOn = new ArrayList<>();
    for (LocalDate day = LocalDate.of(2050, 3, 1); !day.isAfter(LocalDate.of(2050, 7, 3)); day = day.plusDays(1)) {
      // 09:00 in Lagos on that day.
      Clock clock = Clock.fixed(day.atTime(9, 0).atZone(ZoneId.of("Africa/Lagos")).toInstant(), ZoneId.of("UTC"));
      new DeadlineReminderService(jdbc, notifications, txManager, clock).runAll();
      long sentFor = count("select count(*) from compliance.deadline_reminders where deadline_id = '" + id + "'");
      if (sentFor > firedOn.size()) {
        firedOn.add(day.toString());
      }
    }
    assertThat(firedOn).containsExactly("2050-04-01", "2050-05-01", "2050-05-31", "2050-06-16", "2050-06-23", "2050-06-29", "2050-07-01");
    assertThat(count("select count(*) from compliance.deadline_reminders where deadline_id = '" + id + "' and kind = 'reminder'")).isEqualTo(6);
    assertThat(inbox(owner, "DEADLINE_REMINDER") - before).isEqualTo(6);
    assertThat(inbox(owner, "DEADLINE_OVERDUE")).isGreaterThanOrEqualTo(1);
    JsonNode after = data(mvc.perform(get("/v1/compliance/deadlines/" + id).header("Authorization", officer())));
    assertThat(after.path("status").asText()).isEqualTo("overdue");
    assertThat(after.path("reminders").size()).isEqualTo(7);
    assertThat(after.path("reminders").get(0).path("daysBefore").asInt()).isEqualTo(90);
    // A deadline created late gets the most recent interval only — never a backlog.
    JsonNode late = data(send("POST", "/v1/compliance/deadlines", officer(), deadlineBody("2050-09-10", "")).andExpect(status().isCreated()));
    Clock aug31 = Clock.fixed(LocalDate.of(2050, 8, 31).atTime(9, 0).atZone(ZoneId.of("Africa/Lagos")).toInstant(), ZoneId.of("UTC"));
    new DeadlineReminderService(jdbc, notifications, txManager, aug31).runAll();
    assertThat(count("select string_agg(days_before::text, ',') from compliance.deadline_reminders where deadline_id = '"
        + late.path("id").asText() + "'")).isEqualTo(14);
  }

  @Test
  void manualReminderRunNeedsConfigurePermission() throws Exception {
    mvc.perform(post("/v1/compliance/deadlines/reminders/run").header("Authorization", bearerForRole("ESG_MANAGER"))).andExpect(status().isForbidden());
    mvc.perform(post("/v1/compliance/deadlines/reminders/run").header("Authorization", officer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.tenants").value(1));
  }

  // ---------------------------------------------------------------- regulatory changes

  @Test
  void regulatoryChangeImpactAssessmentWorkflow() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    String body = "{\"framework\":\"ISSB_S2\",\"regulator\":\"FRCN\",\"title\":\"ISSB adoption roadmap phase 2\",\"summary\":\"Mandatory for PIEs\","
        + "\"sourceUrl\":\"https://frcnigeria.gov.ng\",\"publishedDate\":\"2026-03-01\",\"effectiveDate\":\"2027-01-01\",\"assessmentDue\":\"2026-04-01\","
        + "\"ownerUserId\":\"" + roleUserId("ESG_DIRECTOR") + "\"}";
    send("POST", "/v1/compliance/changes", officer(), body.replace("https://frcnigeria.gov.ng", "ftp://x"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.sourceUrl").exists());
    send("POST", "/v1/compliance/changes", officer(), body.replace("\"2027-01-01\"", "\"2025-01-01\""))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.effectiveDate").exists());
    long inboxBefore = inbox(roleUserId("ESG_DIRECTOR"), "REGULATORY_CHANGE_ASSIGNED");
    JsonNode c = data(send("POST", "/v1/compliance/changes", officer(), body).andExpect(status().isCreated()));
    String id = c.path("id").asText();
    assertThat(c.path("status").asText()).isEqualTo("new");
    assertThat(c.path("ownerName").asText()).isEqualTo("Esg Director Fixture");
    assertThat(inbox(roleUserId("ESG_DIRECTOR"), "REGULATORY_CHANGE_ASSIGNED") - inboxBefore).isEqualTo(1);

    c = data(send("PUT", "/v1/compliance/changes/" + id + "/start", director, "{\"version\":" + c.path("version").asLong() + "}").andExpect(status().isOk()));
    assertThat(c.path("status").asText()).isEqualTo("assessing");
    send("PUT", "/v1/compliance/changes/" + id + "/assess", director, "{\"version\":" + c.path("version").asLong()
        + ",\"impactLevel\":\"severe\",\"impactAssessment\":\"\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.impactLevel").exists())
        .andExpect(jsonPath("$.errors.impactAssessment").exists());
    c = data(send("PUT", "/v1/compliance/changes/" + id + "/assess", director, "{\"version\":" + c.path("version").asLong()
        + ",\"impactLevel\":\"high\",\"impactAssessment\":\"Climate disclosures become mandatory from FY2027\","
        + "\"requiredActions\":\"Scope 3 inventory; board training\"}").andExpect(status().isOk()));
    assertThat(c.path("status").asText()).isEqualTo("assessed");
    assertThat(c.path("assessedBy").asText()).isEqualTo("Esg Director Fixture");
    // Phase 2: the assessor is told why they can't sign off; the officer isn't.
    assertThat(c.path("closeBlockedReason").asText()).isEqualTo("You assessed this change — another person must sign it off");
    JsonNode asOfficer = data(mvc.perform(get("/v1/compliance/changes/" + id).header("Authorization", officer())));
    assertThat(asOfficer.path("closeBlockedReason").isMissingNode() || asOfficer.path("closeBlockedReason").isNull()).isTrue();
    // Edits are locked once assessed; the assessor can't sign off their own assessment.
    send("PUT", "/v1/compliance/changes/" + id, officer(), body.replace("}", ",\"version\":" + c.path("version").asLong() + "}"))
        .andExpect(status().isConflict());
    send("PUT", "/v1/compliance/changes/" + id + "/close", director, "{\"version\":" + c.path("version").asLong() + "}")
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-SOD"));
    send("PUT", "/v1/compliance/changes/" + id + "/close", bearerForRole("ESG_MANAGER"), "{\"version\":" + c.path("version").asLong() + "}")
        .andExpect(status().isForbidden());
    c = data(send("PUT", "/v1/compliance/changes/" + id + "/close", officer(), "{\"version\":" + c.path("version").asLong() + "}").andExpect(status().isOk()));
    assertThat(c.path("status").asText()).isEqualTo("closed");
    assertThat(c.path("closedBy").asText()).isEqualTo("Compliance Officer Fixture");
    // Deleting is only for unassessed changes.
    mvc.perform(delete("/v1/compliance/changes/" + id + "?version=" + c.path("version").asLong()).header("Authorization", officer()))
        .andExpect(status().isConflict());
    c = data(send("PUT", "/v1/compliance/changes/" + id + "/reopen", officer(), "{\"version\":" + c.path("version").asLong() + "}").andExpect(status().isOk()));
    assertThat(c.path("status").asText()).isEqualTo("assessing");
    assertThat(absent(c.path("closedAt"))).isTrue();

    JsonNode assessing = data(mvc.perform(get("/v1/compliance/changes?status=assessing").header("Authorization", bearerForRole("AUDITOR"))));
    assertThat(assessing.findValuesAsText("id")).contains(id);
    mvc.perform(get("/v1/compliance/changes/" + id).header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B)))
        .andExpect(status().isNotFound());
  }

  @Test
  void ownersAreActiveUsersWhoCanUpdateCompliance() throws Exception {
    JsonNode owners = data(mvc.perform(get("/v1/compliance/owners").header("Authorization", officer())).andExpect(status().isOk()));
    List<String> ids = owners.findValuesAsText("id");
    assertThat(ids).contains(roleUserId("COMPLIANCE_OFFICER"), roleUserId("ESG_DIRECTOR")).doesNotContain(roleUserId("AUDITOR"));
  }

  // ---------------------------------------------------------------- helpers

  /** Null fields are left out of the JSON. */
  private static boolean absent(JsonNode n) {
    return n.isMissingNode() || n.isNull();
  }

  private static long inbox(String userId, String type) throws Exception {
    return count("select count(*) from notifications.notification_queue where user_id = '" + userId + "' and notification_type = '" + type + "'");
  }

  private static long count(String sql) throws Exception {
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      // RLS is forced for the owner too: look inside tenant A.
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      ResultSet rs = st.executeQuery(sql);
      rs.next();
      String v = rs.getString(1);
      return v == null ? 0 : Long.parseLong(v.contains(",") ? v.substring(v.lastIndexOf(',') + 1) : v);
    }
  }
}
