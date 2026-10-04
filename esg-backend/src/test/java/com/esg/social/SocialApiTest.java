package com.esg.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** WP-33 M7 Social Impact through the API: workforce (CBN P4), safety (journey 16), community (17), grievances (18). */
class SocialApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;
  @Autowired private GrievanceService grievanceService;
  @Autowired private VariableEngine variables;

  private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Lagos"));
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

  private JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  private ResultActions send(MockHttpServletRequestBuilder b, String token, String body) throws Exception {
    return mvc.perform(b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode read(String path, String token) throws Exception {
    return data(mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk()));
  }

  private ResultActions act(String path, String token, String action, String extra, long version) throws Exception {
    return send(post(path + "/actions"), token, "{\"action\":\"" + action + "\",\"version\":" + version + (extra == null ? "" : "," + extra) + "}");
  }

  private static String snapshotBody(String org, int year, String hours, String lines) {
    return "{\"orgId\":\"" + org + "\",\"reportingPeriodStart\":\"" + year + "-01-01\",\"reportingPeriodEnd\":\"" + year + "-12-31\","
        + "\"hoursWorked\":" + hours + ",\"newHires\":6,\"leavers\":4,\"payCurrency\":\"NGN\",\"lines\":[" + lines + "]}";
  }

  private static String line(String level, String gender, String age, int n, int disabled, String pay) {
    return "{\"level\":\"" + level + "\",\"gender\":\"" + gender + "\",\"ageBand\":\"" + age + "\",\"headcount\":" + n
        + ",\"disabledHeadcount\":" + disabled + ",\"totalAnnualPay\":" + pay + "}";
  }

  private static final String LINES = String.join(",",
      line("senior_management", "female", "30_50", 3, 0, "45000000"), line("senior_management", "male", "over_50", 5, 0, "90000000"),
      line("non_management", "female", "under_30", 12, 1, "36000000"), line("non_management", "male", "under_30", 20, 1, "70000000"));

  private static String incident(String org, String type, String outcome, String person, String occurred, Integer lostDays) {
    return "{\"orgId\":\"" + org + "\",\"title\":\"Incident " + type + " " + outcome + "\",\"incidentType\":\"" + type + "\",\"outcome\":\""
        + outcome + "\"" + (person == null ? "" : ",\"personType\":\"" + person + "\"") + ",\"occurredAt\":\"" + occurred + "\""
        + (lostDays == null ? "" : ",\"lostDays\":" + lostDays) + ",\"location\":\"Workshop\"}";
  }

  // ---------------------------------------------------------------- workforce → CBN NSBP P4

  @Test
  void workforceFiguresNeedASecondPersonToApproveAndThenFeedCbnPrinciple4() throws Exception {
    int year = 2021;
    asOwnerInTenantA("update social.workforce_snapshots set deleted_at = now() where deleted_at is null and extract(year from reporting_period_end) = "
        + year);
    String manager = bearerForRole("ESG_MANAGER");
    String director = bearerForRole("ESG_DIRECTOR");
    String admin = bearerForRole("TENANT_ADMIN");
    // Validation mirrors the aggregate model (no personal identifiers, only counts and pay totals).
    send(post("/v1/social/workforce"), manager, snapshotBody(ORG_SITE_1, year, "1000", line("staff", "female", "30_50", 1, 2, "null") + ","
        + line("non_management", "x", "30_50", 1, 0, "null"))).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['lines[0].level']").exists()).andExpect(jsonPath("$.errors['lines[1].gender']").exists());
    send(post("/v1/social/workforce"), manager, snapshotBody(ORG_SITE_1, year, "1000", line("non_management", "female", "30_50", 1, 2, "null") + ","
        + line("non_management", "female", "30_50", 3, 0, "null"))).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['lines[0].disabledHeadcount']").exists()).andExpect(jsonPath("$.errors['lines[1]']").exists());
    send(post("/v1/social/workforce"), manager, snapshotBody(ORG_SITE_1, year, "1000", LINES).replace("\"payCurrency\":\"NGN\",", ""))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.payCurrency").exists());
    send(post("/v1/social/workforce"), manager, snapshotBody(ORG_SITE_1, year, "1000", "")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.lines").exists());
    // Organisation scope: the manager works at Site 1 only.
    send(post("/v1/social/workforce"), manager, snapshotBody(ORG_HQ, year, "1000", LINES)).andExpect(status().isForbidden());

    // Before anything is approved, P4 has no data.
    assertThat(p4(year)).isEqualTo("missing");
    JsonNode s = data(send(post("/v1/social/workforce"), manager, snapshotBody(ORG_SITE_1, year, "96000", LINES)).andExpect(status().isCreated()));
    String id = s.path("id").asText();
    assertThat(s.path("status").asText()).isEqualTo("draft");
    assertThat(s.path("metrics").path("headcount").asInt()).isEqualTo(40);
    assertThat(s.path("metrics").path("femalePct").decimalValue()).isEqualByComparingTo("37.5");
    assertThat(s.path("metrics").path("womenInManagementPct").decimalValue()).isEqualByComparingTo("37.5");
    // 3 women in senior management: that level's gap is withheld; overall (15 women, 25 men) is shown.
    assertThat(s.path("metrics").path("levels").get(0).path("payGapNote").asText()).contains("fewer than 5");
    assertThat(s.path("metrics").path("payGapPct").isNumber()).isTrue();

    s = data(act("/v1/social/workforce/" + id, manager, "submit", null, s.path("version").asLong()).andExpect(status().isOk()));
    assertThat(s.path("status").asText()).isEqualTo("submitted");
    // Frozen once submitted; the manager can't approve (no social:approve) — refused and audited.
    send(put("/v1/social/workforce/" + id), manager, snapshotBody(ORG_SITE_1, year, "96000", LINES).replace("}]}", "}],\"version\":"
        + s.path("version").asLong() + "}")).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-STATE"));
    act("/v1/social/workforce/" + id, manager, "approve", null, s.path("version").asLong()).andExpect(status().isForbidden());
    s = data(act("/v1/social/workforce/" + id, director, "approve", null, s.path("version").asLong()).andExpect(status().isOk()));
    assertThat(s.path("status").asText()).isEqualTo("approved");
    assertThat(s.path("approvedByName").asText()).isEqualTo("Esg Director Fixture");

    // Approved figures evidence CBN NSBP P4 (narrative: partial until attested) and fill the template's metric variables.
    assertThat(p4(year)).isEqualTo("partial");
    Map<String, VariableValue> v = asTenantA(() -> variables.resolve(Set.of("metric:employees_female", "metric:women_in_management_pct",
        "metric:employees_total", "social:pay_gap_pct", "social:women_pct", "social:ltifr"), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), "t"));
    assertThat(v.get("metric:employees_female").value()).isEqualByComparingTo("15");
    assertThat(v.get("metric:employees_total").display()).isEqualTo("40");
    assertThat(v.get("metric:women_in_management_pct").display()).isEqualTo("37.5%");
    assertThat(v.get("metric:employees_female").basis()).contains("approved workforce figures");
    assertThat(v.get("social:women_pct").value()).isEqualByComparingTo("37.5");
    assertThat(v.get("social:pay_gap_pct").status()).isEqualTo("ok");
    // 96,000 hours and no incidents in 2021: a zero rate, not a missing one.
    assertThat(v.get("social:ltifr").value()).isEqualByComparingTo("0");

    // Segregation of duties: the submitter can't approve their own figures.
    JsonNode other = data(send(post("/v1/social/workforce"), director, snapshotBody(ORG_SITE_2, year, "1000", LINES)).andExpect(status().isCreated()));
    other = data(act("/v1/social/workforce/" + other.path("id").asText(), director, "submit", null, other.path("version").asLong()));
    assertThat(other.path("blockedActions").path("approve").asText()).contains("someone else");
    act("/v1/social/workforce/" + other.path("id").asText(), director, "approve", null, other.path("version").asLong())
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-SOD"));
    // Sending back needs a reason; the tenant admin (another approver) can approve.
    act("/v1/social/workforce/" + other.path("id").asText(), admin, "return", null, other.path("version").asLong())
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.note").exists());
    act("/v1/social/workforce/" + other.path("id").asText(), admin, "approve", null, other.path("version").asLong()).andExpect(status().isOk());
    // A locked period refuses new figures.
    send(post("/v1/social/workforce"), director, snapshotBody(ORG_SITE_2, 2020, "1000", LINES)).andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ESG-409-LOCKED"));
  }

  private String p4(int year) throws Exception {
    JsonNode gaps = read("/v1/compliance/gaps?framework=CBN_NSBP&year=" + year, bearerForRole("COMPLIANCE_OFFICER"));
    for (JsonNode r : gaps.path("requirements")) {
      if ("P4".equals(r.path("code").asText())) {
        return r.path("status").asText();
      }
    }
    throw new AssertionError("no P4");
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

  // ---------------------------------------------------------------- journey 16: incident → LTIFR recalculated

  @Test
  void journey16SafetyIncidentRecalculatesLtifrAndTrir() throws Exception {
    int year = 2022;
    asOwnerInTenantA("update social.workforce_snapshots set deleted_at = now() where deleted_at is null and extract(year from reporting_period_end) = " + year,
        "update social.safety_incidents set deleted_at = now() where deleted_at is null and extract(year from reporting_period_start) = " + year);
    String manager = bearerForRole("ESG_MANAGER");
    String director = bearerForRole("ESG_DIRECTOR");
    // 400,000 hours worked at Site 1, approved.
    JsonNode s = data(send(post("/v1/social/workforce"), manager, snapshotBody(ORG_SITE_1, year, "400000", LINES)).andExpect(status().isCreated()));
    s = data(act("/v1/social/workforce/" + s.path("id").asText(), manager, "submit", null, s.path("version").asLong()));
    act("/v1/social/workforce/" + s.path("id").asText(), director, "approve", null, s.path("version").asLong()).andExpect(status().isOk());
    JsonNode stats = read("/v1/social/safety/statistics?year=" + year, director);
    assertThat(stats.path("hoursWorked").decimalValue()).isEqualByComparingTo("400000");
    assertThat(stats.path("ltifr").decimalValue()).isEqualByComparingTo("0");
    assertThat(stats.path("trir").decimalValue()).isEqualByComparingTo("0");

    // Validation follows the classification rules.
    send(post("/v1/social/incidents"), manager, incident(ORG_SITE_1, "injury", "none", "employee", year + "-03-01T09:00", null))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.outcome").exists());
    send(post("/v1/social/incidents"), manager, incident(ORG_SITE_1, "injury", "lost_time", "employee", year + "-03-01T09:00", null))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.lostDays").exists());
    send(post("/v1/social/incidents"), manager, incident(ORG_SITE_1, "near_miss", "lost_time", null, year + "-03-01T09:00", 2))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.outcome").exists());
    send(post("/v1/social/incidents"), manager, incident(ORG_SITE_1, "injury", "first_aid", null, TODAY.plusDays(2) + "T09:00", null))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.personType").exists()).andExpect(jsonPath("$.errors.occurredAt").exists());
    send(post("/v1/social/incidents"), manager, incident(ORG_SITE_1, "injury", "first_aid", "employee", "2020-06-01T09:00", null))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-LOCKED"));

    // A lost-time injury: LTIFR = 1 × 1,000,000 ÷ 400,000 = 2.50; TRIR = 1 × 200,000 ÷ 400,000 = 0.50. Approvers are told.
    long before = inbox(roleUserId("ESG_DIRECTOR"), "SAFETY_INCIDENT_SERIOUS");
    JsonNode lti = data(send(post("/v1/social/incidents"), manager, incident(ORG_SITE_1, "injury", "lost_time", "employee", year + "-03-01T09:00", 3))
        .andExpect(status().isCreated()));
    assertThat(lti.path("recordable").asBoolean()).isTrue();
    assertThat(lti.path("lostTime").asBoolean()).isTrue();
    assertThat(inbox(roleUserId("ESG_DIRECTOR"), "SAFETY_INCIDENT_SERIOUS")).isEqualTo(before + 1);
    stats = read("/v1/social/safety/statistics?year=" + year, director);
    assertThat(stats.path("lostTimeInjuries").asInt()).isEqualTo(1);
    assertThat(stats.path("ltifr").decimalValue()).isEqualByComparingTo("2.50");
    assertThat(stats.path("trir").decimalValue()).isEqualByComparingTo("0.50");
    // A medical-treatment case is recordable but not lost time; a contractor's injury is counted separately.
    send(post("/v1/social/incidents"), manager, incident(ORG_SITE_1, "injury", "medical_treatment", "employee", year + "-05-10T14:00", null))
        .andExpect(status().isCreated());
    send(post("/v1/social/incidents"), manager, incident(ORG_SITE_1, "injury", "lost_time", "contractor", year + "-05-11T14:00", 2))
        .andExpect(status().isCreated());
    send(post("/v1/social/incidents"), manager, incident(ORG_SITE_1, "near_miss", "none", null, year + "-05-12T14:00", null))
        .andExpect(status().isCreated());
    // An employee injury at a site with no hours recorded is excluded from the rate, and said so.
    send(post("/v1/social/incidents"), director, incident(ORG_SITE_2, "injury", "restricted_work", "employee", year + "-06-01T08:00", null))
        .andExpect(status().isCreated());
    stats = read("/v1/social/safety/statistics?year=" + year, director);
    assertThat(stats.path("recordableCases").asInt()).isEqualTo(2);
    assertThat(stats.path("ltifr").decimalValue()).isEqualByComparingTo("2.50");
    assertThat(stats.path("trir").decimalValue()).isEqualByComparingTo("1.00");
    assertThat(stats.path("contractorRecordable").asInt()).isEqualTo(1);
    assertThat(stats.path("nearMisses").asInt()).isEqualTo(1);
    assertThat(stats.path("excludedIncidents").asInt()).isEqualTo(1);
    assertThat(stats.path("byMonth").get(4).path("incidents").asInt()).as("May").isEqualTo(3);
    assertThat(stats.path("trend").get(4).path("ltifr").decimalValue()).isEqualByComparingTo("2.50");
    // Reclassifying recalculates: the medical case turns out to be lost time → LTIFR 5.00.
    JsonNode medical = null;
    for (JsonNode i : read("/v1/social/incidents?year=" + year + "&orgId=" + ORG_SITE_1, director)) {
      if ("medical_treatment".equals(i.path("outcome").asText())) {
        medical = i;
      }
    }
    send(put("/v1/social/incidents/" + medical.path("id").asText()), manager, incident(ORG_SITE_1, "injury", "lost_time", "employee",
        year + "-05-10T14:00", 1).replace("}", ",\"version\":" + medical.path("version").asLong() + "}")).andExpect(status().isOk());
    assertThat(read("/v1/social/safety/statistics?year=" + year, director).path("ltifr").decimalValue()).isEqualByComparingTo("5.00");
    // The manager doesn't see Site 2's incident.
    String site2 = read("/v1/social/incidents?year=" + year + "&orgId=" + ORG_SITE_2, director).get(0).path("id").asText();
    mvc.perform(get("/v1/social/incidents/" + site2).header("Authorization", manager)).andExpect(status().isNotFound());

    // Investigation: someone who can update social records investigates; findings need a root cause and actions;
    // closing needs social:approve and someone other than whoever recorded the findings.
    String path = "/v1/social/incidents/" + lti.path("id").asText();
    long version = read(path, manager).path("version").asLong();
    act(path, manager, "start_investigation", null, version).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.investigatorId").exists());
    act(path, manager, "start_investigation", "\"investigatorId\":\"" + roleUserId("AUDITOR") + "\"", version).andExpect(status().isBadRequest());
    JsonNode i = data(act(path, manager, "start_investigation", "\"investigatorId\":\"" + roleUserId("ESG_MANAGER") + "\"", version)
        .andExpect(status().isOk()));
    assertThat(i.path("status").asText()).isEqualTo("investigating");
    act(path, manager, "record_findings", "\"rootCause\":\"\"", i.path("version").asLong()).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.rootCause").exists()).andExpect(jsonPath("$.errors.correctiveActions").exists());
    i = data(act(path, manager, "record_findings", "\"rootCause\":\"Unguarded belt\",\"correctiveActions\":\"Guard fitted; toolbox talk\"",
        i.path("version").asLong()).andExpect(status().isOk()));
    assertThat(i.path("status").asText()).isEqualTo("actions_open");
    act(path, manager, "close", null, i.path("version").asLong()).andExpect(status().isForbidden());
    i = data(act(path, director, "close", null, i.path("version").asLong()).andExpect(status().isOk()));
    assertThat(i.path("status").asText()).isEqualTo("closed");
    assertThat(i.path("closedByName").asText()).isEqualTo("Esg Director Fixture");
    act(path, director, "reopen", null, i.path("version").asLong()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.note").exists());

    // Whoever records the findings can't close.
    String p2 = "/v1/social/incidents/" + site2;
    JsonNode j = read(p2, director);
    j = data(act(p2, director, "start_investigation", "\"investigatorId\":\"" + roleUserId("ESG_DIRECTOR") + "\"", j.path("version").asLong()));
    j = data(act(p2, director, "record_findings", "\"rootCause\":\"Wet floor\",\"correctiveActions\":\"Signage\"", j.path("version").asLong()));
    act(p2, director, "close", null, j.path("version").asLong()).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-SOD"));
    act(p2, bearerForRole("TENANT_ADMIN"), "close", null, j.path("version").asLong()).andExpect(status().isOk());

    // Every social report variable resolves for the year (rates and counts from the register; figures from the snapshot).
    Map<String, VariableValue> v = asTenantA(() -> variables.resolve(new java.util.LinkedHashSet<>(SocialMetricsService.VARIABLES.stream()
        .map(k -> "social:" + k).toList()), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), "t"));
    assertThat(v.get("social:ltifr").display()).isEqualTo("5.00 per million hours");
    assertThat(v.get("social:trir").value()).isEqualByComparingTo("1.00");
    assertThat(v.get("social:lost_time_injuries").value()).isEqualByComparingTo("2");
    assertThat(v.get("social:recordable_cases").value()).isEqualByComparingTo("2");
    assertThat(v.get("social:fatalities").value()).isEqualByComparingTo("0");
    assertThat(v.get("social:hours_worked").value()).isEqualByComparingTo("400000");
    assertThat(v.get("social:headcount").value()).isEqualByComparingTo("40");
    assertThat(v.get("social:women_senior_pct").status()).isEqualTo("ok");
    assertThat(v.get("social:disability_pct").value()).isEqualByComparingTo("5.0");
    assertThat(v.get("social:turnover_pct").status()).isEqualTo("ok");
    assertThat(v.get("social:grievances_received").status()).isEqualTo("ok");
    assertThat(v.get("social:grievances_open").status()).isEqualTo("ok");
    assertThat(v.get("social:beneficiaries").status()).isEqualTo("ok");
    assertThat(v.get("social:community_investment").status()).as("no expenditure dated in " + year).isEqualTo("missing");
    Map<String, VariableValue> metrics = asTenantA(() -> variables.resolve(Set.of("metric:recordable_injuries", "metric:work_related_fatalities",
        "metric:hours_worked", "metric:new_hires", "metric:employee_turnover_pct"), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), "t"));
    assertThat(metrics.get("metric:recordable_injuries").value()).isEqualByComparingTo("2");
    assertThat(metrics.get("metric:work_related_fatalities").value()).isEqualByComparingTo("0");
    assertThat(metrics.get("metric:hours_worked").value()).isEqualByComparingTo("400000");
    assertThat(metrics.get("metric:new_hires").value()).isEqualByComparingTo("6");
    assertThat(metrics.get("metric:employee_turnover_pct").value()).isEqualByComparingTo("10.0");
  }

  // ---------------------------------------------------------------- journey 17: community project with geo-tagged evidence

  @Test
  void journey17CommunityProjectWithGeoTaggedEvidence() throws Exception {
    String manager = bearerForRole("ESG_MANAGER");
    String body = "{\"orgId\":\"" + ORG_SITE_1 + "\",\"name\":\"Rumuokoro water scheme\",\"category\":\"water_sanitation\",\"community\":\"Rumuokoro\","
        + "\"stateRegion\":\"Rivers\",\"lga\":\"Obio/Akpor\",\"latitude\":4.8156,\"longitude\":7.0498,\"budget\":10000000,\"currency\":\"NGN\","
        + "\"plannedBeneficiaries\":3000,\"status\":\"active\",\"startDate\":\"2025-01-15\",\"sdgs\":[6,3],\"piaHostCommunity\":true}";
    send(post("/v1/social/projects"), manager, body.replace("\"longitude\":7.0498,", "")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.longitude").exists());
    send(post("/v1/social/projects"), manager, body.replace("\"active\"", "\"completed\"").replace("[6,3]", "[18]")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['sdgs[0]']").exists());
    send(post("/v1/social/projects"), manager, body.replace("\"active\"", "\"completed\"")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.actualBeneficiaries").exists());
    send(post("/v1/social/projects"), manager, body.replace(ORG_SITE_1, ORG_SITE_2)).andExpect(status().isForbidden());
    JsonNode p = data(send(post("/v1/social/projects"), manager, body).andExpect(status().isCreated()));
    String id = p.path("id").asText();
    assertThat(p.path("sdgs").toString()).isEqualTo("[3,6]");

    // Dated expenditure: budget use and community investment for the year; a locked period refuses it.
    send(post("/v1/social/projects/" + id + "/expenditures"), manager, "{\"spentOn\":\"2020-05-01\",\"description\":\"Survey\",\"amount\":100}")
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-LOCKED"));
    send(post("/v1/social/projects/" + id + "/expenditures"), manager, "{\"spentOn\":\"" + TODAY.plusDays(3) + "\",\"description\":\"x\",\"amount\":0}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.amount").exists()).andExpect(jsonPath("$.errors.spentOn").doesNotExist());
    java.math.BigDecimal before = read("/v1/social/overview?year=" + TODAY.getYear(), manager).path("community").path("investmentBase").decimalValue();
    p = data(send(post("/v1/social/projects/" + id + "/expenditures"), manager, "{\"spentOn\":\"" + TODAY + "\",\"description\":\"Borehole drilling\","
        + "\"amount\":2500000}").andExpect(status().isCreated()));
    assertThat(p.path("spent").decimalValue()).isEqualByComparingTo("2500000");
    assertThat(p.path("budgetUsedPct").decimalValue()).isEqualByComparingTo("25.0");
    assertThat(p.path("expenditures").get(0).path("amountBase").decimalValue()).isEqualByComparingTo("2500000");
    assertThat(read("/v1/social/overview?year=" + TODAY.getYear(), manager).path("community").path("investmentBase").decimalValue())
        .isEqualByComparingTo(before.add(new java.math.BigDecimal("2500000")));
    // A project in another currency needs a dated rate — no rate, no guess.
    JsonNode usd = data(send(post("/v1/social/projects"), manager, body.replace("\"NGN\"", "\"XOF\"").replace("Rumuokoro water", "XOF clinic"))
        .andExpect(status().isCreated()));
    send(post("/v1/social/projects/" + usd.path("id").asText() + "/expenditures"), manager, "{\"spentOn\":\"" + TODAY + "\",\"description\":\"x\","
        + "\"amount\":10}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.amount").value(org.hamcrest.Matchers.containsString("exchange rate")));

    // Geo-tagged evidence: the photo is uploaded to the project, then placed; distance from the site is measured.
    String fileId = upload(manager, "community_project", id);
    String elsewhere = upload(manager, "community_project", usd.path("id").asText());
    send(post("/v1/social/projects/" + id + "/evidence"), manager, "{\"fileId\":\"" + elsewhere + "\",\"latitude\":4.82,\"longitude\":7.05,"
        + "\"capturedOn\":\"" + TODAY + "\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.fileId").exists());
    send(post("/v1/social/projects/" + id + "/evidence"), manager, "{\"fileId\":\"" + fileId + "\",\"latitude\":95,\"longitude\":7.05,"
        + "\"capturedOn\":\"" + TODAY + "\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.latitude").exists());
    p = data(send(post("/v1/social/projects/" + id + "/evidence"), manager, "{\"fileId\":\"" + fileId + "\",\"caption\":\"Borehole head\","
        + "\"latitude\":4.8201,\"longitude\":7.0512,\"capturedOn\":\"" + TODAY + "\"}").andExpect(status().isCreated()));
    JsonNode ev = p.path("evidence").get(0);
    assertThat(ev.path("distanceKm").decimalValue()).isLessThan(new java.math.BigDecimal("1"));
    assertThat(ev.path("farFromSite").asBoolean()).isFalse();
    assertThat(ev.path("fileName").asText()).isEqualTo("site.png");
    assertThat(p.path("evidenceCount").asInt()).isEqualTo(1);
    String far = upload(manager, "community_project", id);
    p = data(send(post("/v1/social/projects/" + id + "/evidence"), manager, "{\"fileId\":\"" + far + "\",\"latitude\":6.5244,\"longitude\":3.3792,"
        + "\"capturedOn\":\"" + TODAY + "\"}").andExpect(status().isCreated()));
    assertThat(p.path("evidence").findValues("farFromSite").stream().filter(JsonNode::asBoolean).count()).as("Lagos is ~500 km away").isEqualTo(1);
    // The same file can't be placed twice.
    send(post("/v1/social/projects/" + id + "/evidence"), manager, "{\"fileId\":\"" + far + "\",\"latitude\":6.5,\"longitude\":3.3,"
        + "\"capturedOn\":\"" + TODAY + "\"}").andExpect(status().isConflict());
    // A project with expenditure can't be deleted (cancel it instead).
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/v1/social/projects/" + id + "?version="
        + p.path("version").asLong()).header("Authorization", manager)).andExpect(status().isConflict());
  }

  private String upload(String token, String resourceType, String resourceId) throws Exception {
    String body = mvc.perform(multipart("/v1/files").file(new MockMultipartFile("file", "site.png", "image/png", PNG))
        .param("resourceType", resourceType).param("resourceId", resourceId).header("Authorization", token))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(body).path("data").path("id").asText();
  }

  // ---------------------------------------------------------------- journey 18: grievance raised → escalated → resolved

  @Test
  void journey18GrievanceRaisedEscalatedResolved() throws Exception {
    String manager = bearerForRole("ESG_MANAGER");
    String director = bearerForRole("ESG_DIRECTOR");
    String body = "{\"orgId\":\"" + ORG_SITE_1 + "\",\"community\":\"Rumuokoro\",\"anonymous\":false,\"complainantName\":\"Chief Amadi\","
        + "\"complainantContact\":\"+2348030000000\",\"channel\":\"community_meeting\",\"category\":\"environment\",\"severity\":\"low\","
        + "\"title\":\"Dust from haulage trucks\",\"description\":\"Trucks raise dust near the primary school.\",\"receivedOn\":\"" + TODAY + "\"}";
    send(post("/v1/social/grievances"), manager, body.replace("\"anonymous\":false", "\"anonymous\":true")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.anonymous").exists());
    send(post("/v1/social/grievances"), manager, body.replace("\"Chief Amadi\"", "\"\"")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.complainantName").exists());
    send(post("/v1/social/grievances"), manager, body.replace("}", ",\"assignedTo\":\"" + roleUserId("AUDITOR") + "\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.assignedTo").exists());
    send(post("/v1/social/grievances"), manager, body.replace("\"low\"", "\"urgent\"").replace("\"" + TODAY + "\"", "\"" + TODAY.plusDays(1) + "\""))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.severity").exists());
    send(post("/v1/social/grievances"), bearerForRole("AUDITOR"), body).andExpect(status().isForbidden());

    JsonNode g = data(send(post("/v1/social/grievances"), manager, body).andExpect(status().isCreated()));
    String id = g.path("id").asText();
    String path = "/v1/social/grievances/" + id;
    assertThat(g.path("reference").asText()).matches("GRV-" + TODAY.getYear() + "-\\d{4}");
    assertThat(g.path("dueOn").asText()).as("low: 45-day SLA").isEqualTo(TODAY.plusDays(45).toString());
    assertThat(g.path("escalationLevel").asInt()).isZero();
    assertThat(g.path("complainantName").asText()).isEqualTo("Chief Amadi");
    // Personal data is encrypted at rest and stays out of the list.
    assertThat(ownerValue("select complainant_name from social.grievances where id = '" + id + "'")).doesNotContain("Amadi");
    JsonNode listed = null;
    for (JsonNode s : read("/v1/social/grievances", manager)) {
      if (id.equals(s.path("id").asText())) {
        listed = s;
      }
    }
    assertThat(listed.has("complainantName")).isFalse();

    // A critical grievance is escalated on receipt.
    long before = inbox(roleUserId("ESG_DIRECTOR"), "GRIEVANCE_ESCALATED");
    JsonNode critical = data(send(post("/v1/social/grievances"), manager, body.replace("\"low\"", "\"critical\"").replace("\"anonymous\":false,"
        + "\"complainantName\":\"Chief Amadi\",\"complainantContact\":\"+2348030000000\"", "\"anonymous\":true")).andExpect(status().isCreated()));
    assertThat(critical.path("escalationLevel").asInt()).isEqualTo(1);
    assertThat(critical.path("dueOn").asText()).isEqualTo(TODAY.plusDays(7).toString());
    assertThat(inbox(roleUserId("ESG_DIRECTOR"), "GRIEVANCE_ESCALATED")).isEqualTo(before + 1);

    // Acknowledge → investigate (needs an assignee) → escalate manually (needs a reason).
    g = data(act(path, manager, "acknowledge", null, g.path("version").asLong()).andExpect(status().isOk()));
    assertThat(g.path("status").asText()).isEqualTo("acknowledged");
    act(path, manager, "investigate", null, g.path("version").asLong()).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.assignedTo").exists());
    g = data(send(put(path), manager, body.replace("}", ",\"assignedTo\":\"" + roleUserId("ESG_MANAGER") + "\",\"version\":"
        + g.path("version").asLong() + "}")).andExpect(status().isOk()));
    g = data(act(path, manager, "investigate", null, g.path("version").asLong()).andExpect(status().isOk()));
    assertThat(g.path("assignedToName").asText()).isEqualTo("Esg Manager Fixture");
    act(path, manager, "escalate", null, g.path("version").asLong()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.note").exists());
    g = data(act(path, manager, "escalate", "\"note\":\"School head wrote to the commissioner\"", g.path("version").asLong()).andExpect(status().isOk()));
    assertThat(g.path("escalationLevel").asInt()).isEqualTo(1);
    assertThat(g.path("dueOn").asText()).isEqualTo(TODAY.plusDays(7).toString());

    // Time travel: ten days on it's overdue, so the daily job escalates it to level 2 — once.
    var run = grievanceService.runAll(Clock.offset(Clock.systemUTC(), Duration.ofDays(10)));
    assertThat(run.tenants()).isPositive();
    g = read(path, manager);
    assertThat(g.path("escalationLevel").asInt()).isEqualTo(2);
    assertThat(g.path("overdue").asBoolean()).isFalse();
    grievanceService.runAll(Clock.offset(Clock.systemUTC(), Duration.ofDays(30)));
    assertThat(read(path, manager).path("escalationLevel").asInt()).as("2 is the top level").isEqualTo(2);
    assertThat(g.path("actions").toString()).doesNotContain("escalate");
    g = read(path, manager);

    // Resolve (needs a resolution) → close (social:approve, not the resolver) → reopen when the complainant isn't satisfied.
    act(path, manager, "resolve", null, g.path("version").asLong()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.resolution").exists());
    g = data(act(path, manager, "resolve", "\"resolution\":\"Water bowser on the road twice a day; speed limit agreed\"", g.path("version").asLong())
        .andExpect(status().isOk()));
    assertThat(g.path("status").asText()).isEqualTo("resolved");
    act(path, manager, "close", "\"complainantSatisfied\":\"yes\"", g.path("version").asLong()).andExpect(status().isForbidden());
    act(path, director, "close", null, g.path("version").asLong()).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.complainantSatisfied").exists());
    g = data(act(path, director, "close", "\"complainantSatisfied\":\"no\"", g.path("version").asLong()).andExpect(status().isOk()));
    assertThat(g.path("status").asText()).isEqualTo("closed");
    g = data(act(path, manager, "reopen", "\"note\":\"Dust is back since the bowser stopped\"", g.path("version").asLong()).andExpect(status().isOk()));
    assertThat(g.path("status").asText()).isEqualTo("investigating");
    List<String> trail = new ArrayList<>();
    g.path("updates").forEach(u -> trail.add(u.path("action").asText()));
    assertThat(trail).containsExactly("received", "acknowledge", "investigate", "escalate", "escalated_automatically", "resolve", "close", "reopen");

    // Whoever resolved it can't also close it.
    JsonNode other = data(send(post("/v1/social/grievances"), director, body.replace(ORG_SITE_1, ORG_HQ)).andExpect(status().isCreated()));
    String op = "/v1/social/grievances/" + other.path("id").asText();
    // Settled on the spot (resolved straight from received).
    other = data(act(op, director, "resolve", "\"resolution\":\"Explained the haulage schedule\"", other.path("version").asLong())
        .andExpect(status().isOk()));
    act(op, director, "close", "\"complainantSatisfied\":\"yes\"", other.path("version").asLong()).andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ESG-409-SOD"));
    // An approver can run the overdue check now: a low grievance received 60 days ago is past its 45 days.
    JsonNode old = data(send(post("/v1/social/grievances"), manager, body.replace("\"" + TODAY + "\"", "\"" + TODAY.minusDays(60) + "\""))
        .andExpect(status().isCreated()));
    assertThat(old.path("overdue").asBoolean()).isTrue();
    send(post("/v1/social/grievances/escalate-overdue"), manager, "").andExpect(status().isForbidden());
    JsonNode run2 = data(send(post("/v1/social/grievances/escalate-overdue"), director, "").andExpect(status().isOk()));
    assertThat(run2.path("escalated").asInt()).isPositive();
    assertThat(read("/v1/social/grievances/" + old.path("id").asText(), manager).path("escalationLevel").asInt()).isEqualTo(1);
    // The manager (Site 1) can't see HQ's grievance.
    mvc.perform(get(op).header("Authorization", manager)).andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------- access, overview, dashboard

  @Test
  void readersReadWritersWriteAndOtherTenantsCantSeeTheModule() throws Exception {
    mvc.perform(get("/v1/social/overview").header("Authorization", bearerForRole("AUDITOR"))).andExpect(status().isOk());
    mvc.perform(get("/v1/social/grievances").header("Authorization", bearerForRole("INVESTOR_VIEWER"))).andExpect(status().isForbidden());
    mvc.perform(get("/v1/social/incidents").header("Authorization", bearerForRole("DATA_COLLECTOR"))).andExpect(status().isForbidden());
    send(post("/v1/social/incidents"), bearerForRole("ESG_ANALYST"), incident(ORG_SITE_1, "near_miss", "none", null, TODAY + "T08:00", null))
        .andExpect(status().isForbidden());
    // Tenant B's plan doesn't include Social Impact.
    mvc.perform(get("/v1/social/overview").header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/v1/social/overview?year=1999").header("Authorization", bearerForRole("ESG_DIRECTOR"))).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.year").exists());
    // The executive dashboard shows the Social card to social readers only.
    JsonNode dash = read("/v1/dashboard/executive", bearerForRole("ESG_DIRECTOR"));
    assertThat(dash.path("social").isObject()).isTrue();
    assertThat(dash.path("social").has("openGrievances")).isTrue();
    JsonNode board = read("/v1/dashboard/executive", bearerForRole("BOARD_MEMBER"));
    assertThat(board.path("social").isMissingNode() || board.path("social").isNull()).isTrue();
    assertThat(read("/v1/social/people", bearerForRole("ESG_DIRECTOR")).toString()).contains("Esg Manager Fixture").doesNotContain("Auditor");
  }

  // ---------------------------------------------------------------- helpers

  private static long inbox(String userId, String type) throws Exception {
    return Long.parseLong(ownerValue("select count(*) from notifications.notification_queue where user_id = '" + userId
        + "' and notification_type = '" + type + "'"));
  }

  private static String ownerValue(String sql) throws Exception {
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      ResultSet rs = st.executeQuery(sql);
      rs.next();
      return rs.getString(1);
    }
  }
}
