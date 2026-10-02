package com.esg.crosscutting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Phase 3 missing capability (WP-52d): items 1–8 through the API, against the RLS test database. */
class CapabilityTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;

  private JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  private ResultActions send(MockHttpServletRequestBuilder b, String token, String body) throws Exception {
    return mvc.perform(b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body == null ? "{}" : body));
  }

  private JsonNode point(String token, String metric, String value, String day, String extra) throws Exception {
    return data(send(post("/v1/data/points"), token, "{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"" + metric + "\",\"numericValue\":"
        + value + ",\"reportingPeriodStart\":\"" + day + "\",\"reportingPeriodEnd\":\"" + day + "\"" + extra + "}")
        .andExpect(status().isCreated()));
  }

  private JsonNode step(String token, JsonNode dp, String action) throws Exception {
    return data(send(put("/v1/data/points/" + dp.path("id").asText() + "/" + action), token,
        "{\"version\":" + dp.path("version").asLong() + ",\"reason\":\"test\"}").andExpect(status().isOk()));
  }

  private List<JsonNode> recordsFor(String dataPointId, String day) throws Exception {
    JsonNode page = data(mvc.perform(get("/v1/carbon/emissions?source=data_hub&from=" + day + "&to=" + day)
        .header("Authorization", bearerForRole("ESG_DIRECTOR")))).path("content");
    List<JsonNode> out = new ArrayList<>();
    page.forEach(r -> {
      if (dataPointId.equals(r.path("dataPointId").asText())) {
        out.add(r);
      }
    });
    return out;
  }

  // ---------------------------------------------------------------- (1) approval → emission record, exactly once

  @Test
  void approvedFuelAndElectricityPointsBecomeExactlyOneRecordEachAndACorrectionSupersedesIt() throws Exception {
    String collector = bearerForRole("DATA_COLLECTOR");
    String analyst = bearerForRole("ESG_ANALYST");
    String director = bearerForRole("ESG_DIRECTOR");
    Map<String, String> scopes = Map.of("diesel_litres", "scope_1", "petrol_litres", "scope_1", "lpg_kg", "scope_1",
        "natural_gas_m3", "scope_1", "electricity_kwh", "scope_2");
    int d = 20;
    for (String metric : List.of("diesel_litres", "petrol_litres", "lpg_kg", "natural_gas_m3", "electricity_kwh")) {
      String day = "2026-01-" + d++;
      JsonNode dp = point(collector, metric, "100", day, "");
      dp = step(collector, dp, "submit");
      dp = step(analyst, dp, "validate");
      step(director, dp, "approve");
      List<JsonNode> records = recordsFor(dp.path("id").asText(), day);
      assertThat(records).as(metric).hasSize(1);
      assertThat(records.get(0).path("scope").asText()).as(metric).isEqualTo(scopes.get(metric));
      assertThat(records.get(0).path("co2eTonnes").decimalValue()).as(metric).isPositive();
    }

    // A correction: reopen, edit, approve again → one live record with the new value, linked to the one it replaces.
    String day = "2026-01-26";
    JsonNode dp = point(collector, "diesel_litres", "100", day, "");
    dp = step(director, step(analyst, step(collector, dp, "submit"), "validate"), "approve");
    String first = recordsFor(dp.path("id").asText(), day).get(0).path("id").asText();
    dp = step(director, dp, "reopen");
    dp = data(send(put("/v1/data/points/" + dp.path("id").asText()), collector, "{\"orgId\":\"" + ORG_SITE_1
        + "\",\"metricKey\":\"diesel_litres\",\"numericValue\":250,\"reportingPeriodStart\":\"" + day + "\",\"reportingPeriodEnd\":\""
        + day + "\",\"version\":" + dp.path("version").asLong() + "}").andExpect(status().isOk()));
    dp = step(director, step(analyst, step(collector, dp, "submit"), "validate"), "approve");
    List<JsonNode> live = recordsFor(dp.path("id").asText(), day);
    assertThat(live).hasSize(1);
    assertThat(live.get(0).path("activityData").decimalValue()).isEqualByComparingTo("250");
    assertThat(live.get(0).path("id").asText()).isNotEqualTo(first);
    mvc.perform(get("/v1/audit/lineage?type=emission_record&id=" + first).header("Authorization", director))
        .andExpect(jsonPath("$.data.downstream[?(@.relation == 'superseded_by')].targetId").value(live.get(0).path("id").asText()));
    // The point's history shows each step, including the reopening.
    JsonNode history = data(mvc.perform(get("/v1/data/points/" + dp.path("id").asText() + "/history").header("Authorization", director)));
    List<String> actions = new ArrayList<>();
    history.forEach(h -> actions.add(h.path("action").asText()));
    assertThat(actions).containsSubsequence("created", "submitted", "validated", "approved", "reopened", "edited", "submitted",
        "validated", "approved");
  }

  // ---------------------------------------------------------------- (2) bulk actions

  @Test
  void bulkStepsCheckEveryPointAndReportPerRow() throws Exception {
    String collector = bearerForRole("DATA_COLLECTOR");
    String analyst = bearerForRole("ESG_ANALYST");
    String director = bearerForRole("ESG_DIRECTOR");
    List<JsonNode> drafts = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      drafts.add(point(collector, "water_withdrawal_m3", String.valueOf(10 + i), "2026-01-1" + i, ""));
    }
    JsonNode submitted = data(send(post("/v1/data/points/bulk"), collector, "{\"action\":\"submit\",\"items\":" + items(drafts) + "}")
        .andExpect(status().isOk()));
    assertThat(submitted.path("succeeded").asInt()).isEqualTo(3);
    assertThat(submitted.path("results").get(0).path("status").asText()).isEqualTo("submitted");

    // One stale version: that row is refused with the single-call code; the others go through.
    List<JsonNode> current = new ArrayList<>();
    for (JsonNode p : drafts) {
      current.add(data(mvc.perform(get("/v1/data/points/" + p.path("id").asText()).header("Authorization", analyst))));
    }
    String body = "{\"action\":\"validate\",\"items\":[" + item(current.get(0)) + "," + item(current.get(1)) + ",{\"id\":\""
        + current.get(2).path("id").asText() + "\",\"version\":999}]}";
    JsonNode validated = data(send(post("/v1/data/points/bulk"), analyst, body).andExpect(status().isOk()));
    assertThat(validated.path("succeeded").asInt()).isEqualTo(2);
    assertThat(validated.path("results").get(2).path("ok").asBoolean()).isFalse();
    assertThat(validated.path("results").get(2).path("errorCode").asText()).isEqualTo("ESG-409-VERSION");

    // Segregation of duties per row: the director validated nothing but entered one point himself.
    JsonNode own = point(director, "water_withdrawal_m3", "99", "2026-01-15", "");
    own = step(director, own, "submit");
    own = step(analyst, own, "validate");
    current.set(0, data(mvc.perform(get("/v1/data/points/" + drafts.get(0).path("id").asText()).header("Authorization", director))));
    JsonNode approved = data(send(post("/v1/data/points/bulk"), director, "{\"action\":\"approve\",\"items\":[" + item(current.get(0))
        + "," + item(own) + "]}").andExpect(status().isOk()));
    assertThat(approved.path("results").get(0).path("status").asText()).isEqualTo("approved");
    assertThat(approved.path("results").get(1).path("errorCode").asText()).isEqualTo("ESG-403-SOD");
    // …and the refusal is in the audit trail like the single call's would be.
    mvc.perform(get("/v1/audit/logs?action=denied&resourceId=" + own.path("id").asText()).header("Authorization", bearerForRole("AUDITOR")))
        .andExpect(jsonPath("$.data.content[0].resource").value("PUT /v1/data/points/{id}/approve"))
        .andExpect(jsonPath("$.data.content[0].newValue.errorCode").value("ESG-403-SOD"));

    // Permission for the step itself, request shape, reject reason.
    send(post("/v1/data/points/bulk"), collector, "{\"action\":\"approve\",\"items\":" + items(List.of(own)) + "}")
        .andExpect(status().isForbidden());
    send(post("/v1/data/points/bulk"), analyst, "{\"action\":\"validate\"}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.items").exists());
    send(post("/v1/data/points/bulk"), analyst, "{\"action\":\"reject\",\"items\":" + items(List.of(own)) + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
    send(post("/v1/data/points/bulk"), analyst, "{\"action\":\"publish\",\"items\":" + items(List.of(own)) + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.action").exists());
    send(post("/v1/data/points/bulk"), analyst, "{\"action\":\"validate\",\"importJobId\":\"" + UUID.randomUUID() + "\"}")
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-STATE"));
  }

  private static String item(JsonNode p) {
    return "{\"id\":\"" + p.path("id").asText() + "\",\"version\":" + p.path("version").asLong() + "}";
  }

  private static String items(List<JsonNode> points) {
    return "[" + String.join(",", points.stream().map(CapabilityTest::item).toList()) + "]";
  }

  // ---------------------------------------------------------------- (3) draft preview

  @Test
  void aDraftReportPreviewsAsAWatermarkedPdfWhileTheExportStaysGated() throws Exception {
    String manager = bearerForRole("ESG_MANAGER");
    JsonNode templates = data(mvc.perform(get("/v1/reports/templates").header("Authorization", manager)));
    String template = null;
    for (JsonNode t : templates) {
      if ("gri_core".equals(t.path("key").asText())) {
        template = t.path("id").asText();
      }
    }
    JsonNode report = data(send(post("/v1/reports"), manager, "{\"templateId\":\"" + template + "\",\"title\":\"Draft preview 2088\","
        + "\"reportingPeriodStart\":\"2088-01-01\",\"reportingPeriodEnd\":\"2088-12-31\"}").andExpect(status().isCreated()));
    String id = report.path("id").asText();

    byte[] pdf = mvc.perform(get("/v1/reports/" + id + "/preview").header("Authorization", bearerForRole("AUDITOR")))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline; filename=\"Draft-preview-2088-v0.1-DRAFT.pdf\"")))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andReturn().getResponse().getContentAsByteArray();
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      PDFTextStripper text = new PDFTextStripper();
      for (int page = 1; page <= doc.getNumberOfPages(); page++) {
        text.setStartPage(page);
        text.setEndPage(page);
        assertThat(text.getText(doc)).as("page " + page).contains("DRAFT");
      }
      text.setStartPage(1);
      text.setEndPage(1);
      assertThat(text.getText(doc)).contains("Draft preview 2088").contains("not approved").contains("Working version v0.1");
    }
    // Not an export: nothing stored, and the final export still needs approval.
    JsonNode after = data(mvc.perform(get("/v1/reports/" + id).header("Authorization", manager)));
    assertThat(after.path("exports")).isEmpty();
    send(post("/v1/reports/" + id + "/generate"), manager, "{\"format\":\"pdf\"}").andExpect(status().isConflict());
    mvc.perform(get("/v1/reports/" + id + "/preview").header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B)))
        .andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------- (4) intensity onboarding

  @Test
  void theCarbonSummaryNamesMissingIntensityDenominatorsAndPendingOnes() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    JsonNode gaps = data(mvc.perform(get("/v1/carbon/emissions/summary?year=2088").header("Authorization", director))).path("intensityGaps");
    assertThat(gaps).hasSize(3);
    assertThat(gaps.get(0).path("denominator").asText()).isEqualTo("revenue");
    assertThat(gaps.get(0).path("metricKey").asText()).isEqualTo("revenue");
    assertThat(gaps.get(1).path("metricKey").asText()).isEqualTo("employees_total");
    assertThat(gaps.get(2).path("metricKey").asText()).isEqualTo("production_output");
    assertThat(gaps.get(0).path("awaitingApproval").asInt()).isZero();

    // Entered and submitted but not approved: the summary says it is waiting, so the next step is the review queue.
    JsonNode dp = point(bearerForRole("DATA_COLLECTOR"), "employees_total", "40", "2088-03-31", "");
    step(bearerForRole("DATA_COLLECTOR"), dp, "submit");
    JsonNode after = data(mvc.perform(get("/v1/carbon/emissions/summary?year=2088").header("Authorization", director))).path("intensityGaps");
    assertThat(after.get(1).path("awaitingApproval").asInt()).isEqualTo(1);
  }

  // ---------------------------------------------------------------- (6) audit log

  @Test
  void theAuditLogFiltersByUserAndExportsCsvWithBeforeAndAfter() throws Exception {
    String auditor = bearerForRole("AUDITOR");
    JsonNode dp = point(bearerForRole("DATA_COLLECTOR"), "waste_generated_t", "12", "2088-05-01", "");
    step(bearerForRole("DATA_COLLECTOR"), dp, "submit");

    JsonNode users = data(mvc.perform(get("/v1/audit/users").header("Authorization", auditor)).andExpect(status().isOk()));
    List<String> ids = new ArrayList<>();
    users.forEach(u -> ids.add(u.path("id").asText()));
    assertThat(ids).contains(roleUserId("DATA_COLLECTOR"));

    String csv = mvc.perform(get("/v1/audit/logs/export?resourceId=" + dp.path("id").asText() + "&userId=" + roleUserId("DATA_COLLECTOR"))
            .header("Authorization", auditor))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "attachment; filename=\"audit-log.csv\""))
        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    String[] lines = csv.split("\r\n");
    assertThat(lines[0]).isEqualTo("When (UTC),User,User email,Action,Table,Record ID,Changes,Error code,Client IP,User agent");
    assertThat(lines).hasSize(3); // created, then submitted (newest first)
    assertThat(lines[1]).contains("\"Changed\"").contains("\"Data points\"").contains("validation_status: draft → submitted");
    assertThat(lines[2]).contains("\"Created\"").contains("numeric_value: 12");
    mvc.perform(get("/v1/audit/logs/export").header("Authorization", bearerForRole("DATA_COLLECTOR"))).andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------- (7) test data hygiene

  @Test
  void requestsMarkedAsATestRunCreateTestDataThatCanBePurged() throws Exception {
    String admin = bearerForRole("TENANT_ADMIN");
    String body = "{\"name\":\"%s\",\"parentOrgId\":\"" + ORG_SITE_2 + "\",\"orgType\":\"facility\",\"countryCode\":\"NG\"}";
    String marked = data(mvc.perform(post("/v1/organisations").header("Authorization", admin).header("X-ESG-Test-Run", "capability-1")
        .contentType(MediaType.APPLICATION_JSON).content(String.format(body, "Test-run site " + UUID.randomUUID())))
        .andExpect(status().isCreated())).path("id").asText();
    String real = data(send(post("/v1/organisations"), admin, String.format(body, "Real site " + UUID.randomUUID()))
        .andExpect(status().isCreated())).path("id").asText();
    // A malformed marker is ignored, not stored.
    String bad = data(mvc.perform(post("/v1/organisations").header("Authorization", admin).header("X-ESG-Test-Run", "x'; drop table")
        .contentType(MediaType.APPLICATION_JSON).content(String.format(body, "Unmarked site " + UUID.randomUUID())))
        .andExpect(status().isCreated())).path("id").asText();
    assertThat(isTest(marked)).isTrue();
    assertThat(isTest(real)).isFalse();
    assertThat(isTest(bad)).isFalse();

    // An approved test point (locked while approved, V018) is purged too: reopened with a reason, then soft-deleted.
    JsonNode approved = data(mvc.perform(post("/v1/data/points").header("Authorization", bearerForRole("DATA_COLLECTOR"))
        .header("X-ESG-Test-Run", "capability-1").contentType(MediaType.APPLICATION_JSON)
        .content("{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"diesel_litres\",\"numericValue\":3,"
            + "\"reportingPeriodStart\":\"2088-07-01\",\"reportingPeriodEnd\":\"2088-07-01\"}")).andExpect(status().isCreated()));
    approved = step(bearerForRole("ESG_DIRECTOR"), step(bearerForRole("ESG_ANALYST"), step(bearerForRole("DATA_COLLECTOR"), approved, "submit"),
        "validate"), "approve");
    assertThat(approved.path("validationStatus").asText()).isEqualTo("approved");

    mvc.perform(delete("/v1/testing/test-data").header("Authorization", bearerForRole("ESG_MANAGER"))).andExpect(status().isForbidden());
    JsonNode purged = data(mvc.perform(delete("/v1/testing/test-data").header("Authorization", admin)).andExpect(status().isOk()));
    int orgs = 0;
    for (JsonNode p : purged) {
      if ("organisations".equals(p.path("entity").asText())) {
        orgs = p.path("purged").asInt();
      }
    }
    assertThat(orgs).isGreaterThanOrEqualTo(1);
    mvc.perform(get("/v1/organisations/" + marked).header("Authorization", admin)).andExpect(status().isNotFound());
    mvc.perform(get("/v1/organisations/" + real).header("Authorization", admin)).andExpect(status().isOk());
    mvc.perform(get("/v1/data/points/" + approved.path("id").asText()).header("Authorization", admin)).andExpect(status().isNotFound());
    // The purge is audited (soft deletes go through the audit trigger).
    mvc.perform(get("/v1/audit/logs?resource=auth.organisations&resourceId=" + marked).header("Authorization", admin))
        .andExpect(jsonPath("$.data.content[0].newValue.deleted_at").exists());
  }

  private static boolean isTest(String orgId) throws Exception {
    try (Connection c = PostgresTestDatabase.ownerConnection(); Statement st = c.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      try (ResultSet rs = st.executeQuery("select is_test from auth.organisations where id = '" + orgId + "'")) {
        rs.next();
        return rs.getBoolean(1);
      }
    }
  }

  // ---------------------------------------------------------------- (8) source system vs responsible person

  @Test
  void aDataSourceIsASystemAndTheResponsiblePersonIsRecordedSeparately() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    String analystId = roleUserId("ESG_ANALYST");
    JsonNode analyst = data(mvc.perform(get("/v1/admin/users/" + analystId).header("Authorization", bearerForRole("TENANT_ADMIN"))));
    String personName = analyst.path("firstName").asText() + " " + analyst.path("lastName").asText();

    // The people who can be named (no platform users).
    JsonNode people = data(mvc.perform(get("/v1/data/owners").header("Authorization", bearerForRole("DATA_COLLECTOR"))).andExpect(status().isOk()));
    List<String> peopleIds = new ArrayList<>();
    people.forEach(p -> peopleIds.add(p.path("id").asText()));
    assertThat(peopleIds).contains(analystId, roleUserId("DATA_COLLECTOR")).doesNotContain(roleUserId("SUPER_ADMIN"));

    // A person's name is not a source system.
    send(post("/v1/data/sources"), director, "{\"name\":\"" + personName + "\",\"sourceType\":\"csv\",\"active\":true}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
    send(post("/v1/data/sources"), director, "{\"name\":\"Meter X\",\"sourceType\":\"meter\",\"active\":true,\"ownerUserId\":\""
        + UUID.randomUUID() + "\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.ownerUserId").exists());

    String name = "Generator meter " + UUID.randomUUID().toString().substring(0, 8);
    JsonNode source = data(send(post("/v1/data/sources"), director, "{\"name\":\"" + name + "\",\"sourceType\":\"meter\",\"active\":true,"
        + "\"ownerUserId\":\"" + analystId + "\"}").andExpect(status().isCreated()));
    assertThat(source.path("sourceType").asText()).isEqualTo("meter");
    assertThat(source.path("ownerName").asText()).isEqualTo(personName);

    // A point from that system is owned by the system's owner unless someone else is named.
    String collector = bearerForRole("DATA_COLLECTOR");
    JsonNode fromMeter = point(collector, "diesel_litres", "5", "2088-06-01", ",\"sourceId\":\"" + source.path("id").asText() + "\"");
    assertThat(fromMeter.path("ownerUserId").asText()).isEqualTo(analystId);
    assertThat(fromMeter.path("ownerName").asText()).isEqualTo(personName);
    assertThat(fromMeter.path("sourceName").asText()).isEqualTo(name);
    JsonNode mine = point(collector, "diesel_litres", "5", "2088-06-02", "");
    assertThat(mine.path("ownerUserId").asText()).isEqualTo(roleUserId("DATA_COLLECTOR"));
    JsonNode named = point(collector, "diesel_litres", "5", "2088-06-03", ",\"ownerUserId\":\"" + roleUserId("ESG_MANAGER") + "\"");
    assertThat(named.path("ownerUserId").asText()).isEqualTo(roleUserId("ESG_MANAGER"));
    send(post("/v1/data/points"), collector, "{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"diesel_litres\",\"numericValue\":5,"
        + "\"reportingPeriodStart\":\"2088-06-04\",\"reportingPeriodEnd\":\"2088-06-04\",\"ownerUserId\":\"" + USER_B_ADMIN + "\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.ownerUserId").exists());
  }

  // ---------------------------------------------------------------- (5) framework content import

  @Test
  void aPlatformAdminImportsFrameworkContentAsAVersionWithDryRunRetirementAndAudit() throws Exception {
    String code = "TEST_FW_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    asOwnerInTenantA("insert into compliance.regulatory_frameworks (code, name, jurisdiction, framework_type, content_status) values ('"
        + code + "', 'Test framework', 'Nigeria', 'voluntary', 'pending_sme')");
    try {
      String platform = bearerForRole("SUPER_ADMIN");
      String v1 = String.join("\n", "requirement_code,title,category,data_type,parent_code,source_ref,metric_keys,description",
          "A,Section A,GEN,narrative,,s.A,,Heading row",
          "A.1,Energy use,E,quantitative,A,s.A.1,energy_total_gj;electricity_kwh,",
          "A.2,Board diversity,G,quantitative,A,s.A.2,board_female_pct,",
          "B,Policy statement,G,narrative,,s.B,,");
      JsonNode dry = data(upload(code, platform, "v1.csv", v1, "1.0", "2026-01-01", true).andExpect(status().isOk()));
      assertThat(dry.path("applied").asBoolean()).isFalse();
      assertThat(dry.path("added")).hasSize(4);
      assertThat(requirements(code)).isEmpty();

      JsonNode applied = data(upload(code, platform, "v1.csv", v1, "1.0", "2026-01-01", false).andExpect(status().isOk()));
      assertThat(applied.path("applied").asBoolean()).isTrue();
      JsonNode reqs = requirements(code);
      assertThat(reqs).hasSize(4);
      assertThat(reqs.get(1).path("parentCode").asText()).isEqualTo("A");
      assertThat(reqs.get(1).path("verificationStatus").asText()).isEqualTo("pending_sme");
      assertThat(reqs.get(1).path("metricKeys").toString()).contains("electricity_kwh", "energy_total_gj");

      // Same label again: refused. Earlier effective date: refused.
      upload(code, platform, "v1.csv", v1, "1.0", "2026-01-01", false).andExpect(status().isConflict())
          .andExpect(jsonPath("$.errorCode").value("ESG-409-CONTENT-VERSION"));
      upload(code, platform, "v1.csv", v1, "0.9", "2025-01-01", false).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("ESG-400-CONTENT-DATES"));

      // v2 as JSON, SME-verified: B is retired (kept, inactive), A.2 changes, C is new.
      String v2 = "{\"requirements\":[{\"requirementCode\":\"A\",\"title\":\"Section A\",\"category\":\"GEN\",\"dataType\":\"narrative\","
          + "\"sourceRef\":\"s.A\",\"description\":\"Heading row\"},{\"requirementCode\":\"A.1\",\"title\":\"Energy use\",\"category\":\"E\","
          + "\"dataType\":\"quantitative\",\"parentCode\":\"A\",\"sourceRef\":\"s.A.1\",\"metricKeys\":[\"energy_total_gj\",\"electricity_kwh\"]},"
          + "{\"requirementCode\":\"A.2\",\"title\":\"Board gender diversity\",\"category\":\"G\",\"dataType\":\"quantitative\",\"parentCode\":\"A\","
          + "\"sourceRef\":\"s.A.2\",\"metricKeys\":[\"board_female_pct\"]},{\"requirementCode\":\"C\",\"title\":\"New item\",\"category\":\"S\","
          + "\"dataType\":\"narrative\",\"sourceRef\":\"s.C\"}]}";
      MockMultipartFile file = new MockMultipartFile("file", "v2.json", "application/json", v2.getBytes(StandardCharsets.UTF_8));
      JsonNode second = data(mvc.perform(multipart("/v1/platform/frameworks/" + code + "/content").file(file).param("versionLabel", "2.0")
          .param("effectiveFrom", "2027-01-01").param("smeVerified", "true").param("verifiedBy", "Dr A. SME")
          .param("source", "Test instrument 2027").param("dryRun", "false").header("Authorization", platform)).andExpect(status().isOk()));
      assertThat(second.path("added").toString()).contains("C");
      assertThat(second.path("changed").toString()).contains("A.2");
      assertThat(second.path("retired").toString()).contains("B");
      JsonNode after = requirements(code);
      List<String> codes = new ArrayList<>();
      after.forEach(r -> codes.add(r.path("code").asText()));
      assertThat(codes).containsExactly("A", "A.1", "A.2", "C");
      assertThat(after.get(0).path("verificationStatus").asText()).isEqualTo("sme_verified");
      JsonNode fw = data(mvc.perform(get("/v1/compliance/frameworks/" + code).header("Authorization", bearerForRole("COMPLIANCE_OFFICER"))));
      assertThat(fw.path("contentStatus").asText()).isEqualTo("sme_verified");
      assertThat(fw.path("version").asText()).isEqualTo("2.0");
      JsonNode versions = data(mvc.perform(get("/v1/compliance/frameworks/" + code + "/content-versions")
          .header("Authorization", bearerForRole("COMPLIANCE_OFFICER"))));
      assertThat(versions).hasSize(2);
      assertThat(versions.get(0).path("versionLabel").asText()).isEqualTo("2.0");
      assertThat(versions.get(0).path("retired").asInt()).isEqualTo(1);
      assertThat(versions.get(0).path("verifiedBy").asText()).isEqualTo("Dr A. SME");
      mvc.perform(get("/v1/audit/logs?resource=compliance.framework_content_versions&resourceId=" + versions.get(0).path("id").asText())
              .header("Authorization", bearerForRole("SUPER_ADMIN")))
          .andExpect(jsonPath("$.data.content[0].newValue.framework").value(code))
          .andExpect(jsonPath("$.data.content[0].newValue.retired").value(1));

      // Row errors come back per line and nothing is written; tenant roles can't import.
      String broken = String.join("\n", "requirement_code,title,category,data_type,parent_code,source_ref,metric_keys,description",
          "X,Ok,E,narrative,,,,", "X,Duplicate,E,narrative,,,,", "Y,Bad,Q,number,Z,,not_a_metric,");
      JsonNode errors = data(upload(code, platform, "bad.csv", broken, "3.0", "2027-06-01", false).andExpect(status().isOk()));
      assertThat(errors.path("applied").asBoolean()).isFalse();
      assertThat(errors.path("errors").toString()).contains("appears twice", "must be E, S, G or GEN", "quantitative or narrative",
          "must be a requirement listed above", "isn't in the metric catalogue");
      upload(code, bearerForRole("TENANT_ADMIN"), "v1.csv", v1, "4.0", "2028-01-01", false).andExpect(status().isForbidden());
      upload(code, platform, "v1.txt", v1, "4.0", "2028-01-01", false).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors.file").exists());
      // Defence in depth: framework content is read-only for the application role, and the write function refuses a
      // caller without framework_content:import even if the API check were bypassed.
      try (Connection app = PostgresTestDatabase.appConnection(); Statement st = app.createStatement()) {
        st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false), "
            + "set_config('app.current_user', '" + roleUserId("TENANT_ADMIN") + "', false)");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> st.execute("select compliance.apply_framework_content("
                + "(select id from compliance.regulatory_frameworks where code = '" + code + "'), '{\"versionLabel\":\"9\","
                + "\"effectiveFrom\":\"2030-01-01\",\"source\":\"x\",\"format\":\"csv\"}', '[]', '{}', null, 'x', 0, 0)"))
            .isInstanceOf(java.sql.SQLException.class)
            .satisfies(e -> assertThat(((java.sql.SQLException) e).getSQLState()).isEqualTo("42501"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> st.execute("update compliance.framework_requirements set title = 'x'"))
            .isInstanceOf(java.sql.SQLException.class);
      }
    } finally {
      asOwnerInTenantA("update compliance.regulatory_frameworks set is_active = false where code = '" + code + "'");
    }
  }

  private ResultActions upload(String code, String token, String name, String content, String label, String from, boolean dryRun)
      throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    return mvc.perform(multipart("/v1/platform/frameworks/" + code + "/content").file(file).param("versionLabel", label)
        .param("effectiveFrom", from).param("source", "Test instrument").param("dryRun", String.valueOf(dryRun))
        .header("Authorization", token));
  }

  private JsonNode requirements(String code) throws Exception {
    return data(mvc.perform(get("/v1/compliance/frameworks/" + code + "/requirements").header("Authorization", bearerForRole("COMPLIANCE_OFFICER")))
        .andExpect(status().isOk()));
  }
}
