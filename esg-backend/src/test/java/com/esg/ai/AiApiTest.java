package com.esg.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * WP-50 Grounded AI through the API (D38): forecasts are built from the tenant's own history and recorded with their
 * engine and inputs; Isolation Forest findings join the anomaly workflow labelled as AI-generated; values read from a
 * report reach the Data Hub only when a person accepts them (corrected or not); when the AI service is down the API
 * says so instead of answering. The AI service is replaced by {@link FakeAiEngine}.
 */
@Import(FakeAiConfig.class)
class AiApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;

  private static final int YEAR = LocalDate.now(ZoneId.of("Africa/Lagos")).getYear();

  @BeforeEach
  void reset() {
    FakeAiEngine.reset();
  }

  private JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  private ResultActions send(MockHttpServletRequestBuilder b, String token, String body) throws Exception {
    return mvc.perform(b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode read(String path, String token) throws Exception {
    return data(mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk()));
  }

  private static String site() throws Exception {
    String org = UUID.randomUUID().toString();
    asOwnerInTenantA("insert into auth.organisations (id, name, org_type, parent_org_id) values ('" + org + "', 'AI site " + org.substring(0, 8)
        + "', 'site', '" + ORG_SITE_1 + "')");
    return org;
  }

  private static String point(String org, String metric, String unit, double value, LocalDate start, LocalDate end, String status) {
    return "insert into data_hub.data_points (org_id, category, sub_category, metric_key, numeric_value, unit, reporting_period_start, "
        + "reporting_period_end, validation_status, created_by) values ('" + org + "', 'E', 'Energy', '" + metric + "', " + value + ", '" + unit
        + "', '" + start + "', '" + end + "', '" + status + "', '" + roleUserId("ESG_DIRECTOR") + "')";
  }

  @Test
  void forecastsComeFromTheTenantsHistoryAndAreRecordedLabelledAndDismissable() throws Exception {
    String manager = bearerForRole("ESG_MANAGER");
    String org = site();
    List<String> sql = new ArrayList<>();
    double[] values = {1200, 1150, 1100, 1060, 1000};
    for (int i = 0; i < values.length; i++) {
      int y = YEAR - 5 + i;
      if (y != 2020) {
        sql.add(point(org, "lpg_kg", "kg", values[i], LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31), "approved"));
      }
    }
    asOwnerInTenantA(sql.toArray(String[]::new));

    assertThat(read("/v1/analytics/ai/status", manager).path("reachable").asBoolean()).isTrue();
    String body = "{\"target\":\"metric\",\"metricKey\":\"lpg_kg\",\"frequency\":\"year\",\"horizon\":2,\"levelPct\":80}";
    send(post("/v1/analytics/predictions"), manager, body.replace("\"horizon\":2", "\"horizon\":11")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.horizon").exists());
    send(post("/v1/analytics/predictions"), manager, body.replace("\"year\"", "\"month\"")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.frequency").exists());
    send(post("/v1/analytics/predictions"), manager, body.replace("lpg_kg", "no_such_metric")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.metricKey").exists());
    JsonNode run = data(send(post("/v1/analytics/predictions"), manager, body).andExpect(status().isCreated()));
    // Labelled, traceable: the engine, the parameters and the exact history sent are on record.
    assertThat(run.path("aiGenerated").asBoolean()).isTrue();
    assertThat(run.path("kind").asText()).isEqualTo("forecast");
    assertThat(run.path("engine").asText()).isEqualTo("OLS linear trend · fake statsmodels");
    assertThat(run.path("parameters").path("horizon").asInt()).isEqualTo(2);
    assertThat(run.path("inputSummary").path("metricName").asText()).isNotBlank();
    assertThat(run.path("inputSummary").path("to").asText()).isEqualTo(String.valueOf(YEAR - 1));
    List<AiEngine.SeriesPoint> sent = FakeAiEngine.lastSeries.get();
    assertThat(sent.get(sent.size() - 1)).isEqualTo(new AiEngine.SeriesPoint(String.valueOf(YEAR - 1), 1000.0));
    assertThat(run.path("inputSummary").path("history").size()).isEqualTo(sent.size());
    assertThat(run.path("output").path("points").size()).isEqualTo(2);
    assertThat(run.path("createdByName").asText()).isEqualTo("Esg Manager Fixture");

    // Too little history: the service's reason, as a field error; emissions are sent as 60 months.
    send(post("/v1/analytics/predictions"), manager, body.replace("lpg_kg", "whistleblowing_reports")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.target").value(startsWith("at least 3 years of data are needed")));
    send(post("/v1/analytics/predictions"), bearerForRole("ESG_DIRECTOR"), "{\"target\":\"emissions_total\",\"frequency\":\"month\",\"horizon\":3,\"levelPct\":80}");
    assertThat(FakeAiEngine.lastSeries.get()).hasSize(60);

    // Overridable: dismissed with a reason, kept on record.
    String id = run.path("id").asText();
    send(post("/v1/analytics/predictions/" + id + "/dismiss"), manager, "{\"note\":\"\",\"version\":" + run.path("version").asLong() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.note").exists());
    send(post("/v1/analytics/predictions/" + id + "/dismiss"), manager, "{\"note\":\"Plant closure next year not in the history\",\"version\":99}")
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-VERSION"));
    send(post("/v1/analytics/predictions/" + id + "/dismiss"), manager, "{\"note\":\"Plant closure next year not in the history\",\"version\":"
        + run.path("version").asLong() + "}").andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewStatus").value("dismissed"));
    assertThat(read("/v1/analytics/predictions", bearerForRole("AUDITOR")).toString()).contains(id);

    // Who may: analytics:read to see, analytics:create to run.
    send(post("/v1/analytics/predictions"), bearerForRole("AUDITOR"), body).andExpect(status().isForbidden());
    mvc.perform(get("/v1/analytics/predictions").header("Authorization", bearerForRole("DATA_COLLECTOR"))).andExpect(status().isForbidden());
    mvc.perform(get("/v1/analytics/predictions/" + id).header("Authorization", bearer(USER_B_ADMIN, com.esg.support.PostgresTestDatabase.TENANT_B)))
        .andExpect(status().isForbidden()); // tenant B's plan has no Analytics

    // The service down: said plainly, nothing invented.
    FakeAiEngine.reachable = false;
    send(post("/v1/analytics/predictions"), manager, body).andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("ESG-503-AI-UNAVAILABLE"));
    assertThat(read("/v1/analytics/ai/status", manager).path("reachable").asBoolean()).isFalse();
  }

  @Test
  void isolationForestFindingsJoinTheAnomalyWorkflowLabelledAsAi() throws Exception {
    String manager = bearerForRole("ESG_MANAGER");
    String org = site();
    List<String> sql = new ArrayList<>();
    for (int m = 1; m <= 9; m++) {
      LocalDate start = LocalDate.of(YEAR, m, 1);
      sql.add(point(org, "natural_gas_m3", "m3", m == 7 ? FakeAiEngine.SPIKE : 500 + m, start, start.plusMonths(1).minusDays(1), "submitted"));
    }
    asOwnerInTenantA(sql.toArray(String[]::new));
    send(post("/v1/analytics/ml-anomalies/scan"), manager, "{\"year\":" + YEAR + ",\"contaminationPct\":25}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.contaminationPct").exists());
    JsonNode scan = data(send(post("/v1/analytics/ml-anomalies/scan"), manager, "{\"year\":" + YEAR + ",\"contaminationPct\":5}")
        .andExpect(status().isOk()));
    assertThat(scan.path("newFlags").asInt()).isGreaterThanOrEqualTo(1);
    JsonNode flag = null;
    for (JsonNode a : read("/v1/analytics/anomalies?year=" + YEAR + "&kind=ml", manager)) {
      if (a.path("orgId").asText().equals(org)) {
        flag = a;
      }
    }
    assertThat(flag).isNotNull();
    assertThat(flag.path("aiGenerated").asBoolean()).isTrue();
    assertThat(flag.path("method").asText()).isEqualTo("iforest");
    assertThat(flag.path("mlScore").decimalValue()).isEqualByComparingTo("1");
    assertThat(flag.path("aiRunId").asText()).isEqualTo(scan.path("runId").asText());
    assertThat(flag.path("detail").asText()).startsWith("AI-generated: isolated by the Isolation Forest model (score 1 of 1");
    assertThat(flag.path("actions").toString()).contains("explain");
    // The run is on record; a second scan doesn't duplicate the flag.
    JsonNode runs = read("/v1/analytics/ml-anomalies/runs", manager);
    assertThat(runs.get(0).path("engine").asText()).isEqualTo("fake IsolationForest");
    JsonNode again = data(send(post("/v1/analytics/ml-anomalies/scan"), manager, "{\"year\":" + YEAR + ",\"contaminationPct\":5}"));
    assertThat(again.path("flagged").asInt()).isEqualTo(scan.path("flagged").asInt());
    assertThat(again.path("newFlags").asInt()).isZero();
    // Dismissed by an approver like any other flag.
    send(post("/v1/analytics/anomalies/" + flag.path("id").asText() + "/actions"), bearerForRole("ESG_DIRECTOR"), "{\"action\":\"dismiss\",\"note\":\"Meter replaced; reading "
        + "confirmed\",\"version\":" + flag.path("version").asLong() + "}").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("dismissed"));
    send(post("/v1/analytics/ml-anomalies/scan"), bearerForRole("ESG_ANALYST"), "{\"year\":" + YEAR + ",\"contaminationPct\":5}")
        .andExpect(status().isForbidden());
  }

  private static byte[] pdf() throws Exception {
    try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      doc.addPage(new PDPage());
      doc.save(out);
      return out.toByteArray();
    }
  }

  private String upload(String token, String name, String type, byte[] content) throws Exception {
    return data(mvc.perform(multipart("/v1/files").file(new MockMultipartFile("file", name, type, content)).header("Authorization", token))
        .andExpect(status().isCreated())).path("id").asText();
  }

  private JsonNode await(String id, String token) throws Exception {
    for (int i = 0; i < 100; i++) {
      JsonNode x = read("/v1/analytics/extractions/" + id, token);
      if (!"running".equals(x.path("run").path("status").asText())) {
        return x;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("extraction still running");
  }

  @Test
  void extractedValuesReachTheDataHubOnlyWhenAPersonAcceptsThem() throws Exception {
    String manager = bearerForRole("ESG_MANAGER");
    String report = upload(manager, "annual-report-" + UUID.randomUUID() + ".pdf", "application/pdf", pdf());
    String csv = upload(manager, "figures.csv", "text/csv", "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8));
    FakeAiEngine.extraction.set(json.readTree("""
        {"model": "granite4:micro (Ollama)", "pagesTotal": 12, "pagesRead": [4, 7],
         "values": [
           {"page": 4, "metric_key": "electricity_kwh", "value": 12450, "raw_value": "12,450", "unit": "kWh", "year": %d,
            "quote": "Grid electricity use was 12,450 kWh.", "confidence": "high"},
           {"page": 7, "metric_key": "diesel_litres", "value": 3200, "raw_value": "3,200", "unit": "L", "year": null,
            "quote": "Generators burned 3,200 L of diesel.", "confidence": "medium"}],
         "dropped": [{"page": 7, "metric_key": "water_withdrawal_m3", "reason": "the quote isn't on the page"}]}
        """.formatted(YEAR - 1)));

    send(post("/v1/analytics/extractions"), manager, "{\"fileId\":\"" + csv + "\"}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.fileId").value("choose a PDF report"));
    send(post("/v1/analytics/extractions"), bearerForRole("AUDITOR"), "{\"fileId\":\"" + report + "\"}").andExpect(status().isForbidden());
    JsonNode run = data(send(post("/v1/analytics/extractions"), manager, "{\"fileId\":\"" + report + "\"}").andExpect(status().isAccepted()));
    String id = run.path("id").asText();
    assertThat(run.path("status").asText()).isEqualTo("running");
    JsonNode x = await(id, manager);
    assertThat(x.path("run").path("status").asText()).isEqualTo("completed");
    assertThat(x.path("run").path("engine").asText()).isEqualTo("granite4:micro (Ollama)");
    assertThat(x.path("run").path("output").path("dropped").get(0).path("reason").asText()).isEqualTo("the quote isn't on the page");
    assertThat(x.path("run").path("fileName").asText()).startsWith("annual-report-");
    assertThat(x.path("values")).hasSize(2);
    JsonNode electricity = x.path("values").get(0);
    JsonNode diesel = x.path("values").get(1);
    assertThat(electricity.path("page").asInt()).isEqualTo(4);
    assertThat(electricity.path("quote").asText()).isEqualTo("Grid electricity use was 12,450 kWh.");
    assertThat(electricity.path("status").asText()).isEqualTo("proposed");
    // Nothing reached the Data Hub yet.
    assertThat(electricity.path("dataPointId").isMissingNode() || electricity.path("dataPointId").isNull()).isTrue();

    // Accepted with a correction: a draft data point with the report as evidence and the quote in its notes.
    String base = "/v1/analytics/extractions/" + id + "/values/";
    String accept = "{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"electricity_kwh\",\"value\":12500,\"periodStart\":\"" + (YEAR - 1)
        + "-01-01\",\"periodEnd\":\"" + (YEAR - 1) + "-12-31\",\"note\":\"Rounded figure in the report\",\"version\":";
    send(post(base + electricity.path("id").asText() + "/accept"), manager, accept + "99}").andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ESG-409-VERSION"));
    JsonNode accepted = data(send(post(base + electricity.path("id").asText() + "/accept"), manager, accept + electricity.path("version").asLong() + "}")
        .andExpect(status().isOk()));
    assertThat(accepted.path("status").asText()).isEqualTo("accepted");
    assertThat(accepted.path("reviewNote").asText()).isEqualTo("corrected before acceptance");
    JsonNode point = read("/v1/data/points/" + accepted.path("dataPointId").asText(), manager);
    assertThat(point.path("validationStatus").asText()).isEqualTo("draft");
    assertThat(point.path("numericValue").decimalValue()).isEqualByComparingTo("12500");
    assertThat(point.path("notes").asText()).startsWith("AI-extracted from annual-report-").contains("page 4").contains("Grid electricity use was 12,450 kWh.")
        .contains("Confirmed by Esg Manager Fixture").contains("corrected from electricity_kwh = 12450").contains("Rounded figure in the report");
    assertThat(point.toString()).contains(report);
    send(post(base + electricity.path("id").asText() + "/accept"), manager, accept + accepted.path("version").asLong() + "}")
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-STATE"));

    // Rejected: stays on record, never enters the Data Hub.
    send(post(base + diesel.path("id").asText() + "/reject"), manager, "{\"note\":\"Group figure, not ours\",\"version\":" + diesel.path("version").asLong()
        + "}").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("rejected"));
    assertThat(read("/v1/analytics/extractions", manager).toString()).contains(id);

    // A report the service can't read: the run fails with its reason.
    FakeAiEngine.extractionFailure.set(new AiEngine.AiRefused("the PDF has no text layer (a scanned document); text recognition isn't supported"));
    JsonNode failed = await(data(send(post("/v1/analytics/extractions"), manager, "{\"fileId\":\"" + report + "\"}")).path("id").asText(), manager);
    assertThat(failed.path("run").path("status").asText()).isEqualTo("failed");
    assertThat(failed.path("run").path("error").asText()).contains("no text layer");
    // No language model loaded: refused up front.
    FakeAiEngine.llm = false;
    send(post("/v1/analytics/extractions"), manager, "{\"fileId\":\"" + report + "\"}").andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("ESG-503-AI-UNAVAILABLE"));
  }
}
