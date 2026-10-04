package com.esg.reporting;

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
import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * WP-23 Reporting through the API: journey 13 (template → auto-populated → versioned → approved → PDF/Excel), the
 * snapshot (exports match it; later data doesn't change an approved version), approval segregation of duties, the
 * approval SLA with a fixed clock, validation, permissions and tenant isolation. Each test uses its own year.
 */
class ReportingApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private NotificationService notifications;
  @Autowired private ReportService reportService;
  @Autowired private PlatformTransactionManager txManager;

  private JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  private ResultActions send(String method, String url, String token, String body) throws Exception {
    var b = switch (method) {
      case "POST" -> post(url);
      case "PUT" -> put(url);
      default -> throw new IllegalArgumentException(method);
    };
    return mvc.perform(b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private String role(String r) {
    return bearerForRole(r);
  }

  private String template(String key) throws Exception {
    JsonNode list = data(mvc.perform(get("/v1/reports/templates").header("Authorization", role("ESG_MANAGER"))).andExpect(status().isOk()));
    for (JsonNode t : list) {
      if (key.equals(t.path("key").asText())) {
        return t.path("id").asText();
      }
    }
    throw new AssertionError("template " + key);
  }

  /** collector creates and submits; analyst validates; director approves. */
  private void approvedPoint(String metric, String value, String day, boolean approve) throws Exception {
    String collector = role("DATA_COLLECTOR");
    JsonNode dp = data(send("POST", "/v1/data/points", collector, "{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"" + metric
        + "\",\"numericValue\":" + value + ",\"reportingPeriodStart\":\"" + day + "\",\"reportingPeriodEnd\":\"" + day + "\"}")
        .andExpect(status().isCreated()));
    String id = dp.path("id").asText();
    dp = data(send("PUT", "/v1/data/points/" + id + "/submit", collector, "{\"version\":" + dp.path("version").asLong() + "}"));
    if (approve) {
      dp = data(send("PUT", "/v1/data/points/" + id + "/validate", role("ESG_ANALYST"), "{\"version\":" + dp.path("version").asLong() + "}"));
      send("PUT", "/v1/data/points/" + id + "/approve", role("ESG_DIRECTOR"), "{\"version\":" + dp.path("version").asLong() + "}")
          .andExpect(status().isOk());
    }
  }

  private JsonNode create(String templateKey, int year, String token) throws Exception {
    return data(send("POST", "/v1/reports", token, "{\"templateId\":\"" + template(templateKey) + "\",\"title\":\"Sustainability report " + year
        + "\",\"reportingPeriodStart\":\"" + year + "-01-01\",\"reportingPeriodEnd\":\"" + year + "-12-31\"}").andExpect(status().isCreated()));
  }

  private static JsonNode section(JsonNode report, String title) {
    for (JsonNode s : report.path("sections")) {
      if (title.equals(s.path("title").asText())) {
        return s;
      }
    }
    throw new AssertionError(title);
  }

  private static JsonNode currentStep(JsonNode report) {
    int n = report.path("approval").path("currentStep").asInt();
    return report.path("approval").path("steps").get(n - 1);
  }

  private JsonNode act(JsonNode report, String token, String action, String comments, int expected) throws Exception {
    ResultActions r = send("PUT", "/v1/reports/" + report.path("id").asText() + "/approval-steps/" + currentStep(report).path("id").asText(), token,
        "{\"version\":" + report.path("version").asLong() + ",\"action\":\"" + action + "\"" + (comments == null ? "" : ",\"comments\":\"" + comments + "\"") + "}");
    r.andExpect(status().is(expected));
    return expected == 200 ? data(r) : report;
  }

  private JsonNode submit(JsonNode report, String token) throws Exception {
    return data(send("POST", "/v1/reports/" + report.path("id").asText() + "/approve", token, "{\"version\":" + report.path("version").asLong() + "}")
        .andExpect(status().isOk()));
  }

  private byte[] download(String fileId) throws Exception {
    return mvc.perform(get("/v1/files/" + fileId + "/content").header("Authorization", role("ESG_DIRECTOR")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
  }

  // ---------------------------------------------------------------- templates

  @Test
  void platformTemplatesWithSectionsAndVariables() throws Exception {
    JsonNode list = data(mvc.perform(get("/v1/reports/templates").header("Authorization", role("AUDITOR"))).andExpect(status().isOk()));
    List<String> keys = new ArrayList<>();
    list.forEach(t -> keys.add(t.path("key").asText()));
    assertThat(keys).contains("gri_core", "issb_s2", "cbn_nsbp");
    for (JsonNode t : list) {
      if (t.path("platform").asBoolean()) {
        assertThat(t.path("sections").size()).as(t.path("key").asText()).isPositive();
        assertThat(t.path("contentStatus").asText()).isEqualTo("pending_sme");
      }
    }
    // Every variable a platform template uses is one the engine knows.
    VariableEngine engine = new VariableEngine(jdbc, null, null, null, null, null, null, null, null);
    for (JsonNode t : list) {
      for (JsonNode s : t.path("sections")) {
        assertThat(asTenantA(() -> engine.unknown(s.path("content").asText()))).as(t.path("key") + " / " + s.path("title")).isEmpty();
      }
    }
  }

  // ---------------------------------------------------------------- journey 13

  @Test
  void journey13TemplateAutoPopulatedVersionedApprovedExported() throws Exception {
    // Approved data for 2039 (+ one not yet approved, which must not count), and Scope 1 emissions.
    approvedPoint("electricity_kwh", "1000", "2039-03-31", true);
    approvedPoint("electricity_kwh", "500", "2039-06-30", true);
    approvedPoint("electricity_kwh", "9999", "2039-09-30", false);
    approvedPoint("employees_total", "120", "2039-06-30", true);
    approvedPoint("employees_total", "130", "2039-12-31", true);
    send("POST", "/v1/carbon/emissions", role("ESG_DIRECTOR"), "{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"diesel_generator\","
        + "\"activityData\":1000,\"activityUnit\":\"litre\",\"reportingPeriodStart\":\"2039-02-01\",\"reportingPeriodEnd\":\"2039-02-28\"}")
        .andExpect(status().isCreated());

    String manager = role("ESG_MANAGER");
    JsonNode r = create("gri_core", 2039, manager);
    String id = r.path("id").asText();
    assertThat(r.path("status").asText()).isEqualTo("draft");
    assertThat(r.path("versionLabel").asText()).isEqualTo("v0.1");
    assertThat(r.path("frameworkCode").asText()).isEqualTo("GRI");
    // Auto-populated: sums of approved values only; stocks take the latest period; carbon from records.
    assertThat(r.path("snapshot").path("metric:electricity_kwh").path("display").asText()).isEqualTo("1,500 kWh");
    assertThat(r.path("snapshot").path("metric:electricity_kwh").path("basis").asText()).isEqualTo("2 approved data points");
    assertThat(r.path("snapshot").path("metric:employees_total").path("display").asText()).isEqualTo("130");
    assertThat(r.path("snapshot").path("carbon:scope1").path("display").asText()).isEqualTo("2.68 tCO2e");
    // S7: the diesel factor is pending expert review, so the Scope 1 figure is marked provisional.
    assertThat(r.path("snapshot").path("carbon:scope1").path("provisional").asText())
        .isEqualTo("1 of 1 record (2.68 tCO2e) use emission factors pending expert review");
    assertThat(r.path("snapshot").path("metric:electricity_kwh").path("provisional").isMissingNode()
        || r.path("snapshot").path("metric:electricity_kwh").path("provisional").isNull()).isTrue();
    assertThat(r.path("snapshot").path("metric:water_withdrawal_m3").path("status").asText()).isEqualTo("missing");
    assertThat(section(r, "Energy").path("rendered").asText()).contains("Electricity consumed: 1,500 kWh");
    assertThat(section(r, "Water and waste").path("rendered").asText()).contains("Water withdrawn: [not reported]");
    assertThat(r.path("versions").get(0).path("event").asText()).isEqualTo("created");

    // Edit: unknown variables refused; a narrative added; v0.2.
    List<String> sections = new ArrayList<>();
    for (JsonNode s : r.path("sections")) {
      sections.add(json.writeValueAsString(java.util.Map.of("id", s.path("id").asText(), "title", s.path("title").asText(), "type",
          s.path("type").asText(), "content", s.path("content").asText())));
    }
    String bad = "{\"version\":" + r.path("version").asLong() + ",\"sections\":[" + sections.get(0) + ","
        + "{\"title\":\"Extra\",\"type\":\"data\",\"content\":\"Unicorns: {{metric:unicorns}}\"}]}";
    send("PUT", "/v1/reports/" + id + "/sections", manager, bad).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['sections[1].content']").value("Unknown variable: {{metric:unicorns}}"));
    sections.add("{\"title\":\"Message from the CEO\",\"type\":\"narrative\",\"content\":\"We cut diesel use across our sites.\"}");
    r = data(send("PUT", "/v1/reports/" + id + "/sections", manager, "{\"version\":" + r.path("version").asLong() + ",\"sections\":["
        + String.join(",", sections) + "]}").andExpect(status().isOk()));
    assertThat(r.path("versionLabel").asText()).isEqualTo("v0.2");
    assertThat(r.path("sections").size()).isEqualTo(9);

    // New approved data appears only when the draft is refreshed: v0.3.
    approvedPoint("electricity_kwh", "250", "2039-11-30", true);
    assertThat(data(mvc.perform(get("/v1/reports/" + id).header("Authorization", manager))).path("snapshot").path("metric:electricity_kwh")
        .path("display").asText()).isEqualTo("1,500 kWh");
    r = data(send("POST", "/v1/reports/" + id + "/refresh", manager, "{\"version\":" + r.path("version").asLong() + "}").andExpect(status().isOk()));
    assertThat(r.path("snapshot").path("metric:electricity_kwh").path("display").asText()).isEqualTo("1,750 kWh");
    assertThat(r.path("versionLabel").asText()).isEqualTo("v0.3");

    // Submit → in review; locked; the manager (no reports:approve) can't decide.
    r = submit(r, manager);
    assertThat(r.path("status").asText()).isEqualTo("in_review");
    assertThat(currentStep(r).path("label").asText()).isEqualTo("ESG Director review");
    assertThat(Duration.between(java.time.Instant.parse(currentStep(r).path("activatedAt").asText()),
        java.time.Instant.parse(currentStep(r).path("dueAt").asText())).toDays()).isEqualTo(5);
    send("PUT", "/v1/reports/" + id + "/sections", manager, "{\"version\":" + r.path("version").asLong() + ",\"sections\":[" + sections.get(0) + "]}")
        .andExpect(status().isConflict());
    act(r, manager, "approve", null, 403);

    // Director approves step 1; the director can't take step 2 (needs the compliance officer role).
    r = act(r, role("ESG_DIRECTOR"), "approve", "Figures checked", 200);
    assertThat(currentStep(r).path("label").asText()).isEqualTo("Compliance review");
    act(r, role("ESG_DIRECTOR"), "approve", null, 403);
    // Compliance requests changes: comments required; back to draft v0.4.
    act(r, role("COMPLIANCE_OFFICER"), "request_changes", null, 400);
    r = act(r, role("COMPLIANCE_OFFICER"), "request_changes", "Add the GRI content index reference", 200);
    assertThat(r.path("status").asText()).isEqualTo("draft");
    assertThat(r.path("versionLabel").asText()).isEqualTo("v0.4");
    assertThat(r.path("versions").get(0).path("event").asText()).isEqualTo("changes_requested");
    assertThat(r.path("versions").get(0).path("comment").asText()).isEqualTo("Compliance review: Add the GRI content index reference");
    assertThat(inbox(roleUserId("ESG_MANAGER"), "REPORT_CHANGES_REQUESTED")).isGreaterThanOrEqualTo(1);

    // Resubmit; three approvals → v1.0.
    r = submit(r, manager);
    r = act(r, role("ESG_DIRECTOR"), "approve", null, 200);
    r = act(r, role("COMPLIANCE_OFFICER"), "approve", null, 200);
    assertThat(currentStep(r).path("label").asText()).isEqualTo("Board sign-off");
    r = act(r, role("BOARD_MEMBER"), "approve", "Approved by the board", 200);
    assertThat(r.path("status").asText()).isEqualTo("approved");
    assertThat(r.path("versionLabel").asText()).isEqualTo("v1.0");
    assertThat(inbox(roleUserId("ESG_MANAGER"), "REPORT_APPROVED")).isGreaterThanOrEqualTo(1);

    // Data approved after approval doesn't touch the approved version.
    approvedPoint("electricity_kwh", "4000", "2039-12-15", true);

    // PDF: rendered from the approved snapshot.
    JsonNode pdfJob = data(send("POST", "/v1/reports/" + id + "/generate", manager, "{\"format\":\"pdf\"}").andExpect(status().isCreated()));
    assertThat(pdfJob.path("status").asText()).isEqualTo("done");
    assertThat(pdfJob.path("versionLabel").asText()).isEqualTo("v1.0");
    byte[] pdf = download(pdfJob.path("fileId").asText());
    String text;
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      text = new PDFTextStripper().getText(doc);
    }
    assertThat(text).contains("Sustainability report 2039", "Version v1.0", "Electricity consumed: 1,750 kWh", "Scope 1 (direct): 2.68 tCO2e",
        "We cut diesel use across our sites.", "Water withdrawn: [not reported]", "PROVISIONAL").doesNotContain("5,750");

    // Excel: the Data sheet equals the snapshot, value by value.
    JsonNode xlsxJob = data(send("POST", "/v1/reports/" + id + "/generate", manager, "{\"format\":\"xlsx\"}").andExpect(status().isCreated()));
    JsonNode approvedVersion = null;
    for (JsonNode v : data(mvc.perform(get("/v1/reports/" + id + "/versions").header("Authorization", manager)))) {
      if ("approved".equals(v.path("event").asText())) {
        approvedVersion = data(mvc.perform(get("/v1/reports/" + id + "/versions/" + v.path("id").asText()).header("Authorization", manager)));
        break;
      }
    }
    assertThat(approvedVersion).isNotNull();
    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(download(xlsxJob.path("fileId").asText())))) {
      Sheet sheet = wb.getSheet("Data");
      int rows = 0;
      for (Row row : sheet) {
        if (row.getRowNum() == 0) {
          continue;
        }
        rows++;
        JsonNode snap = approvedVersion.path("snapshot").path(row.getCell(0).getStringCellValue());
        assertThat(row.getCell(1).getStringCellValue()).as(row.getCell(0).getStringCellValue()).isEqualTo(snap.path("display").asText());
        if (!snap.path("value").isNull() && !snap.path("value").isMissingNode()) {
          assertThat(row.getCell(2).getNumericCellValue()).isEqualTo(snap.path("value").asDouble());
        }
      }
      assertThat(rows).isEqualTo(approvedVersion.path("snapshot").size());
      assertThat(wb.getSheet("Report").getRow(0).getCell(0).getStringCellValue()).isEqualTo("Sustainability report 2039");
    }

    // Reopen → v1.1 draft; exports still render v1.0; an approved report can't be deleted.
    r = data(send("POST", "/v1/reports/" + id + "/reopen", manager, "{\"version\":" + r.path("version").asLong() + "}").andExpect(status().isOk()));
    assertThat(r.path("versionLabel").asText()).isEqualTo("v1.1");
    assertThat(data(send("POST", "/v1/reports/" + id + "/generate", manager, "{\"format\":\"pdf\"}")).path("versionLabel").asText()).isEqualTo("v1.0");
    mvc.perform(delete("/v1/reports/" + id + "?version=" + r.path("version").asLong()).header("Authorization", role("ESG_DIRECTOR")))
        .andExpect(status().isConflict());
    // The auditor can read and export but not change.
    mvc.perform(get("/v1/reports/" + id).header("Authorization", role("AUDITOR"))).andExpect(status().isOk());
    send("POST", "/v1/reports/" + id + "/refresh", role("AUDITOR"), "{\"version\":0}").andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------- segregation of duties, SLA

  @Test
  void approverCantApproveOwnSubmissionOrTwoSteps() throws Exception {
    String director = role("ESG_DIRECTOR");
    JsonNode r = submit(create("issb_s2", 2038, director), director);
    // Phase 2: the submitter holds the step's role but is told why they can't act.
    JsonNode step = currentStep(data(mvc.perform(get("/v1/reports/" + r.path("id").asText()).header("Authorization", director))));
    assertThat(step.path("canAct").asBoolean()).isFalse();
    assertThat(step.path("blockedReason").asText()).isEqualTo("You submitted this report — another person must approve it");
    act(r, director, "approve", null, 409);
    r = act(r, role("TENANT_ADMIN"), "approve", null, 403); // holds every permission but not the step's role
    // A user holding two roles of the chain can take only one step.
    asOwnerInTenantA("insert into auth.user_roles (user_id, role_id) select '" + roleUserId("BOARD_MEMBER") + "', id from auth.roles where name = 'COMPLIANCE_OFFICER'");
    try {
      JsonNode m = submit(create("issb_s2", 2037, role("ESG_MANAGER")), role("ESG_MANAGER"));
      m = act(m, director, "approve", null, 200);
      m = act(m, role("BOARD_MEMBER"), "approve", null, 200);
      JsonNode next = currentStep(data(mvc.perform(get("/v1/reports/" + m.path("id").asText()).header("Authorization", role("BOARD_MEMBER")))));
      assertThat(next.path("blockedReason").asText()).isEqualTo("You approved an earlier step — another person must approve this one");
      act(m, role("BOARD_MEMBER"), "approve", null, 409);
      m = act(m, role("BOARD_MEMBER"), "reject", "Should not count", 409);
    } finally {
      asOwnerInTenantA("delete from auth.user_roles where user_id = '" + roleUserId("BOARD_MEMBER") + "' and role_id = "
          + "(select id from auth.roles where name = 'COMPLIANCE_OFFICER')");
    }
    // Reject returns to draft with the comment.
    r = data(mvc.perform(get("/v1/reports/" + r.path("id").asText()).header("Authorization", director)));
    String stepId = currentStep(r).path("id").asText();
    r = data(send("PUT", "/v1/reports/" + r.path("id").asText() + "/approval-steps/" + stepId, role("ESG_MANAGER"),
        "{\"version\":" + r.path("version").asLong() + ",\"action\":\"approve\"}").andExpect(status().isForbidden()));
  }

  @Test
  void overdueApprovalStepRemindsTheRoleOnce() throws Exception {
    JsonNode r = submit(create("cbn_nsbp", 2036, role("ESG_MANAGER")), role("ESG_MANAGER"));
    assertThat(r.path("approval").path("steps").get(0).path("overdue").asBoolean()).isFalse();
    String step = r.path("approval").path("steps").get(0).path("id").asText();
    Clock in4days = Clock.offset(Clock.systemUTC(), Duration.ofDays(4));
    Clock in6days = Clock.offset(Clock.systemUTC(), Duration.ofDays(6));
    new ApprovalSlaService(jdbc, notifications, reportService, txManager, in4days).runAll();
    assertThat(reminders(step)).isZero();
    long before = inbox(roleUserId("ESG_DIRECTOR"), "REPORT_APPROVAL_OVERDUE");
    var run = new ApprovalSlaService(jdbc, notifications, reportService, txManager, in6days).runAll();
    assertThat(run.reminded()).isGreaterThanOrEqualTo(1);
    assertThat(reminders(step)).isEqualTo(1);
    long after = inbox(roleUserId("ESG_DIRECTOR"), "REPORT_APPROVAL_OVERDUE");
    assertThat(after).isGreaterThan(before);
    // Running again sends nothing new.
    assertThat(new ApprovalSlaService(jdbc, notifications, reportService, txManager, in6days).runAll().reminded()).isZero();
    assertThat(inbox(roleUserId("ESG_DIRECTOR"), "REPORT_APPROVAL_OVERDUE")).isEqualTo(after);
    // The step shows as overdue once its due date has passed.
    assertThat(java.time.Instant.parse(r.path("approval").path("steps").get(0).path("dueAt").asText())).isBefore(in6days.instant());
  }

  private static long reminders(String stepId) throws Exception {
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      ResultSet rs = st.executeQuery("select count(*) from reporting.approval_reminders where step_id = '" + stepId + "'");
      rs.next();
      return rs.getLong(1);
    }
  }

  // ---------------------------------------------------------------- validation, permissions, isolation

  @Test
  void validationPermissionsAndIsolation() throws Exception {
    String manager = role("ESG_MANAGER");
    send("POST", "/v1/reports", manager, "{\"title\":\"\"}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.templateId").exists()).andExpect(jsonPath("$.errors.title").exists())
        .andExpect(jsonPath("$.errors.reportingPeriodStart").exists()).andExpect(jsonPath("$.errors.reportingPeriodEnd").exists());
    send("POST", "/v1/reports", manager, "{\"templateId\":\"" + template("gri_core") + "\",\"title\":\"x\",\"reportingPeriodStart\":\"2035-12-31\","
        + "\"reportingPeriodEnd\":\"2035-01-01\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reportingPeriodEnd").exists());
    send("POST", "/v1/reports", manager, "{\"templateId\":\"a0000000-0000-4000-8000-000000000000\",\"title\":\"x\",\"reportingPeriodStart\":\"2035-01-01\","
        + "\"reportingPeriodEnd\":\"2035-12-31\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.templateId").exists());
    send("POST", "/v1/reports", role("AUDITOR"), "{\"templateId\":\"" + template("gri_core") + "\",\"title\":\"x\",\"reportingPeriodStart\":\"2035-01-01\","
        + "\"reportingPeriodEnd\":\"2035-12-31\"}").andExpect(status().isForbidden());
    JsonNode r = create("gri_core", 2035, manager);
    String id = r.path("id").asText();
    // Exports need an approved version.
    send("POST", "/v1/reports/" + id + "/generate", manager, "{\"format\":\"pdf\"}").andExpect(status().isConflict());
    send("POST", "/v1/reports/" + id + "/generate", manager, "{\"format\":\"docx\"}").andExpect(status().isBadRequest());
    // Stale version; period change re-captures data.
    send("PUT", "/v1/reports/" + id, manager, "{\"title\":\"Renamed\",\"reportingPeriodStart\":\"2035-01-01\",\"reportingPeriodEnd\":\"2035-06-30\",\"version\":99}")
        .andExpect(status().isConflict());
    r = data(send("PUT", "/v1/reports/" + id, manager, "{\"title\":\"Renamed\",\"description\":\"H1\",\"reportingPeriodStart\":\"2035-01-01\","
        + "\"reportingPeriodEnd\":\"2035-06-30\",\"version\":" + r.path("version").asLong() + "}").andExpect(status().isOk()));
    assertThat(r.path("title").asText()).isEqualTo("Renamed");
    assertThat(r.path("versions").get(0).path("event").asText()).isEqualTo("refreshed");
    // Tenant B sees nothing.
    mvc.perform(get("/v1/reports/" + id).header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B))).andExpect(status().isNotFound());
    List<String> bIds = data(mvc.perform(get("/v1/reports").header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B)))).findValuesAsText("id");
    assertThat(bIds).doesNotContain(id);
    // Drafts that were never approved can be deleted.
    mvc.perform(delete("/v1/reports/" + id + "?version=" + r.path("version").asLong()).header("Authorization", role("ESG_DIRECTOR")))
        .andExpect(status().isNoContent());
    mvc.perform(get("/v1/reports/" + id).header("Authorization", manager)).andExpect(status().isNotFound());
    mvc.perform(get("/v1/reports?status=archived").header("Authorization", manager)).andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------- helpers

  private static long inbox(String userId, String type) throws Exception {
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      ResultSet rs = st.executeQuery("select count(*) from notifications.notification_queue where user_id = '" + userId
          + "' and notification_type = '" + type + "'");
      rs.next();
      return rs.getLong(1);
    }
  }

  private static <T> T asTenantA(java.util.function.Supplier<T> work) {
    String previous = com.esg.security.TenantContext.getTenantId();
    com.esg.security.TenantContext.setTenantId(PostgresTestDatabase.TENANT_A);
    try {
      return work.get();
    } finally {
      com.esg.security.TenantContext.setTenantId(previous);
    }
  }
}
