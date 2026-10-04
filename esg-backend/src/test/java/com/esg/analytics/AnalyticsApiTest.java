package com.esg.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * WP-35 M9 Analytics through the API: a flag raised when a data point is approved, explained and accepted (segregation of
 * duties, locked periods); custom KPIs, benchmark datasets, the maturity index and SDG alignment; and who may do what.
 * Each test works on a fresh site under Site 1, so history from earlier runs never changes the statistics.
 */
class AnalyticsApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;
  @Autowired private AnomalyService anomalyService;

  private static final int YEAR = LocalDate.now(ZoneId.of("Africa/Lagos")).getYear();
  private static final int Y0 = YEAR - 2;   // history year
  private static final int Y1 = YEAR - 1;   // the year scanned
  /** Monthly water use in the history year: 1,200 m3 in all, February 102. */
  private static final int[] MONTHS = {100, 102, 98, 101, 99, 100, 103, 97, 100, 101, 99, 100};

  private JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  private ResultActions send(MockHttpServletRequestBuilder b, String token, String body) throws Exception {
    return mvc.perform(b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode read(String path, String token) throws Exception {
    return data(mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk()));
  }

  /** A new site under Site 1 (inside the manager's and analyst's scope) with a year of approved monthly water data. */
  private static String freshSite() throws Exception {
    String org = UUID.randomUUID().toString();
    List<String> sql = new ArrayList<>();
    sql.add("insert into auth.organisations (id, name, org_type, parent_org_id) values ('" + org + "', 'Analytics site " + org.substring(0, 8)
        + "', 'site', '" + ORG_SITE_1 + "')");
    for (int m = 1; m <= 12; m++) {
      LocalDate start = LocalDate.of(Y0, m, 1);
      sql.add(point(org, "water_consumption_m3", "Water", "m3", MONTHS[m - 1], start, start.plusMonths(1).minusDays(1)));
    }
    sql.add(point(org, "water_consumption_m3", "Water", "m3", 100, LocalDate.of(Y1, 1, 1), LocalDate.of(Y1, 1, 31)));
    sql.add(point(org, "production_output", "Production", "unit", 1000, LocalDate.of(Y0, 1, 1), LocalDate.of(Y0, 12, 31)));
    sql.add(point(org, "production_output", "Production", "unit", 1200, LocalDate.of(Y1, 1, 1), LocalDate.of(Y1, 12, 31)));
    asOwnerInTenantA(sql.toArray(String[]::new));
    return org;
  }

  private static String point(String org, String metric, String sub, String unit, double value, LocalDate start, LocalDate end) {
    return "insert into data_hub.data_points (org_id, category, sub_category, metric_key, numeric_value, unit, reporting_period_start, "
        + "reporting_period_end, validation_status, created_by) values ('" + org + "', 'E', '" + sub + "', '" + metric + "', " + value + ", '" + unit
        + "', '" + start + "', '" + end + "', 'approved', '" + roleUserId("ESG_DIRECTOR") + "')";
  }

  /** The manager enters February's water, the analyst validates, the director approves (which scans it). */
  private JsonNode approvedFebruary(String org, int value) throws Exception {
    String manager = bearerForRole("ESG_MANAGER");
    JsonNode p = data(send(post("/v1/data/points"), manager, "{\"orgId\":\"" + org + "\",\"metricKey\":\"water_consumption_m3\",\"numericValue\":" + value
        + ",\"reportingPeriodStart\":\"" + Y1 + "-02-01\",\"reportingPeriodEnd\":\"" + LocalDate.of(Y1, 2, 1).plusMonths(1).minusDays(1) + "\"}")
        .andExpect(status().isCreated()));
    String id = p.path("id").asText();
    p = data(send(put("/v1/data/points/" + id + "/submit"), manager, "{\"version\":" + p.path("version").asLong() + "}").andExpect(status().isOk()));
    p = data(send(put("/v1/data/points/" + id + "/validate"), bearerForRole("ESG_ANALYST"), "{\"version\":" + p.path("version").asLong() + "}")
        .andExpect(status().isOk()));
    return data(send(put("/v1/data/points/" + id + "/approve"), bearerForRole("ESG_DIRECTOR"), "{\"version\":" + p.path("version").asLong() + "}")
        .andExpect(status().isOk()));
  }

  private static JsonNode flag(JsonNode list, String kind) {
    for (JsonNode a : list) {
      if (kind.equals(a.path("kind").asText())) {
        return a;
      }
    }
    throw new AssertionError("no " + kind + " flag in " + list);
  }

  private ResultActions act(String token, JsonNode flag, String action, String note) throws Exception {
    return send(post("/v1/analytics/anomalies/" + flag.path("id").asText() + "/actions"), token, "{\"action\":\"" + action + "\","
        + (note == null ? "" : "\"note\":\"" + note + "\",") + "\"version\":" + flag.path("version").asLong() + "}");
  }

  // ---------------------------------------------------------------- anomalies

  @Test
  void anOutlierApprovedIsFlaggedThenExplainedAndAccepted() throws Exception {
    String org = freshSite();
    String manager = bearerForRole("ESG_MANAGER");
    String director = bearerForRole("ESG_DIRECTOR");
    long inboxBefore = inbox(roleUserId("ESG_MANAGER"), "ANOMALY_DETECTED");

    // 160 m3 against 13 earlier months of about 100: far outside both tests; and +56.9 % on February a year earlier (102).
    JsonNode point = approvedFebruary(org, 160);
    JsonNode flags = read("/v1/analytics/anomalies?year=" + Y1 + "&orgId=" + org, manager);
    assertThat(flags.size()).isEqualTo(2);
    JsonNode outlier = flag(flags, "outlier");
    assertThat(outlier.path("method").asText()).isEqualTo("both");
    assertThat(outlier.path("historyN").asInt()).isEqualTo(13);
    assertThat(outlier.path("dataPointId").asText()).isEqualTo(point.path("id").asText());
    assertThat(outlier.path("status").asText()).isEqualTo("open");
    assertThat(outlier.path("detail").asText()).startsWith("outside the usual range");
    JsonNode variance = flag(flags, "variance");
    assertThat(variance.path("changePct").decimalValue()).isEqualByComparingTo("56.9");
    assertThat(variance.path("baseline").decimalValue()).isEqualByComparingTo("102");
    assertThat(variance.path("detail").asText()).isEqualTo("up 56.9% on the same period a year earlier (102)");
    // Whoever entered the value hears about each flag.
    assertThat(inbox(roleUserId("ESG_MANAGER"), "ANOMALY_DETECTED")).isEqualTo(inboxBefore + 2);
    // A rescan finds nothing new.
    JsonNode scan = data(send(post("/v1/analytics/anomalies/scan"), manager, "{\"year\":" + Y1 + "}").andExpect(status().isOk()));
    assertThat(scan.path("pointsChecked").asInt()).isPositive();
    assertThat(scan.path("newFlags").asInt()).isZero();

    // Explaining needs analytics:create (the analyst only reads) and an explanation.
    act(bearerForRole("ESG_ANALYST"), outlier, "explain", "x").andExpect(status().isForbidden());
    act(manager, outlier, "explain", " ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.note").exists());
    outlier = data(act(manager, outlier, "explain", "Pipe burst at the wash bay; repaired 20 February").andExpect(status().isOk()));
    assertThat(outlier.path("status").asText()).isEqualTo("explained");
    assertThat(outlier.path("explainedByName").asText()).isEqualTo("Esg Manager Fixture");
    act(manager, outlier, "accept", null).andExpect(status().isForbidden());
    // Sent back with a note, explained again, accepted.
    act(director, outlier, "return", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.note").exists());
    outlier = data(act(director, outlier, "return", "Attach the plumber's invoice").andExpect(status().isOk()));
    assertThat(outlier.path("status").asText()).isEqualTo("open");
    outlier = data(act(manager, outlier, "explain", "Pipe burst; invoice attached").andExpect(status().isOk()));
    act(manager, outlier, "explain", "again").andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-STATE"));
    outlier = data(act(director, outlier, "accept", "Invoice seen").andExpect(status().isOk()));
    assertThat(outlier.path("status").asText()).isEqualTo("accepted");
    assertThat(outlier.path("actions").size()).isZero();

    // Segregation of duties: whoever explains can't accept their own explanation.
    variance = data(act(director, variance, "explain", "Same burst as the outlier").andExpect(status().isOk()));
    assertThat(variance.path("blockedActions").path("accept").asText()).contains("someone else");
    act(director, variance, "accept", null).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-SOD"));
    variance = data(act(bearerForRole("TENANT_ADMIN"), variance, "accept", null).andExpect(status().isOk()));
    assertThat(variance.path("status").asText()).isEqualTo("accepted");
    // The denied step is in the audit trail.
    assertThat(Long.parseLong(ownerValue("select count(*) from audit.audit_logs where resource_id = '" + variance.path("id").asText()
        + "' and action = 'denied'"))).isPositive();

    // Accepted explanations feed reports; the counts too.
    JsonNode counts = read("/v1/analytics/overview?year=" + Y1, director);
    assertThat(counts.path("anomaliesOpen").asInt()).isNotNegative();
    assertThat(Long.parseLong(ownerValue("select count(*) from analytics.anomalies where org_id = '" + org + "' and status = 'accepted'"))).isEqualTo(2);

    // Evidence for an explanation is attached to the flag.
    mvc.perform(multipart("/v1/files").file(new MockMultipartFile("file", "invoice.pdf", "application/pdf", "%PDF-1.7 invoice".getBytes(StandardCharsets.US_ASCII)))
        .param("resourceType", "analytics_anomaly").param("resourceId", outlier.path("id").asText()).header("Authorization", manager))
        .andExpect(status().isCreated());
    assertThat(read("/v1/files?resourceType=analytics_anomaly&resourceId=" + outlier.path("id").asText(), manager).size()).isEqualTo(1);
  }

  @Test
  void aCorrectedValueClearsItsFlagsAndTheNightlyScanRuns() throws Exception {
    String org = freshSite();
    String director = bearerForRole("ESG_DIRECTOR");
    JsonNode point = approvedFebruary(org, 160);
    assertThat(read("/v1/analytics/anomalies?year=" + Y1 + "&orgId=" + org, director).size()).isEqualTo(2);
    // The point is reopened and corrected to 101: the flags clear (they stay in the history as cleared).
    String id = point.path("id").asText();
    JsonNode p = data(send(put("/v1/data/points/" + id + "/reopen"), director, "{\"version\":" + point.path("version").asLong()
        + ",\"reason\":\"Meter misread\"}").andExpect(status().isOk()));
    send(put("/v1/data/points/" + id), bearerForRole("ESG_MANAGER"), "{\"orgId\":\"" + org + "\",\"metricKey\":\"water_consumption_m3\",\"numericValue\":101,"
        + "\"reportingPeriodStart\":\"" + p.path("reportingPeriodStart").asText() + "\",\"reportingPeriodEnd\":\"" + p.path("reportingPeriodEnd").asText()
        + "\",\"version\":" + p.path("version").asLong() + "}").andExpect(status().isOk());
    // Draft again: a scan clears the flags of a point that is no longer submitted or approved.
    data(send(post("/v1/analytics/anomalies/scan"), director, "{\"year\":" + Y1 + "}").andExpect(status().isOk()));
    assertThat(read("/v1/analytics/anomalies?year=" + Y1 + "&orgId=" + org, director).size()).isZero();
    assertThat(read("/v1/analytics/anomalies?year=" + Y1 + "&orgId=" + org + "&status=cleared", director).size()).isEqualTo(2);
    // The nightly job runs tenant by tenant without a signed-in user.
    assertThat(anomalyService.runAll(Clock.systemUTC())).isNotNegative();
  }

  @Test
  void flagsInALockedPeriodCantChange() throws Exception {
    String org = freshSite();
    String id = UUID.randomUUID().toString();
    // FY2020 is locked in the fixture: the flag is written past the lock (as the owner) to show it can't then be worked.
    asOwnerInTenantA("alter table analytics.anomalies disable trigger assert_periods_open",
        "insert into analytics.anomalies (id, org_id, data_point_id, metric_key, reporting_period_start, reporting_period_end, kind, method, value, change_pct) "
            + "values ('" + id + "', '" + org + "', '" + UUID.randomUUID() + "', 'water_consumption_m3', '2020-03-01', '2020-03-31', 'variance', 'yoy', 90, 50)",
        "alter table analytics.anomalies enable trigger assert_periods_open");
    JsonNode a = read("/v1/analytics/anomalies/" + id, bearerForRole("ESG_MANAGER"));
    assertThat(a.path("actions").size()).isZero();
    assertThat(a.path("blockedActions").path("explain").asText()).isEqualTo("The reporting period is locked.");
    act(bearerForRole("ESG_MANAGER"), a, "explain", "x").andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-LOCKED"));
    // Nor can a locked year be given a new maturity snapshot.
    send(post("/v1/analytics/maturity/assessments"), bearerForRole("ESG_MANAGER"), "{\"year\":2020}").andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ESG-409-LOCKED"));
  }

  @Test
  void sensitivityIsConfiguredByTheDirectorWithinLimits() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    JsonNode s = read("/v1/analytics/settings", bearerForRole("ESG_ANALYST"));
    send(put("/v1/analytics/settings"), bearerForRole("ESG_MANAGER"), "{\"zscoreThreshold\":2.5,\"iqrK\":1.5,\"variancePct\":25,\"minHistory\":4}")
        .andExpect(status().isForbidden());
    send(put("/v1/analytics/settings"), director, "{\"zscoreThreshold\":6,\"iqrK\":0.5,\"variancePct\":2,\"minHistory\":2,\"version\":"
        + s.path("version").asLong() + "}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.zscoreThreshold").exists())
        .andExpect(jsonPath("$.errors.iqrK").exists()).andExpect(jsonPath("$.errors.variancePct").exists()).andExpect(jsonPath("$.errors.minHistory").exists());
    JsonNode saved = data(send(put("/v1/analytics/settings"), director, "{\"zscoreThreshold\":2.5,\"iqrK\":2,\"variancePct\":30,\"minHistory\":6,\"version\":"
        + s.path("version").asLong() + "}").andExpect(status().isOk()));
    try {
      assertThat(saved.path("zscoreThreshold").decimalValue()).isEqualByComparingTo("2.5");
      assertThat(saved.path("minHistory").asInt()).isEqualTo(6);
      send(put("/v1/analytics/settings"), director, "{\"zscoreThreshold\":3,\"iqrK\":1.5,\"variancePct\":25,\"minHistory\":4,\"version\":"
          + s.path("version").asLong() + "}").andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-VERSION"));
    } finally {
      send(put("/v1/analytics/settings"), director, "{\"zscoreThreshold\":3,\"iqrK\":1.5,\"variancePct\":25,\"minHistory\":4,\"version\":"
          + saved.path("version").asLong() + "}").andExpect(status().isOk());
    }
  }

  // ---------------------------------------------------------------- KPIs, benchmarks, maturity, SDGs

  private static String kpi(String code, String org, String extra) {
    return "{\"code\":\"" + code + "\",\"name\":\"Water per unit " + code + "\",\"pillar\":\"E\",\"orgId\":\"" + org + "\",\"metricA\":\"water_consumption_m3\","
        + "\"operator\":\"divide\",\"metricB\":\"production_output\",\"multiplier\":1000,\"unit\":\"litres per unit\",\"decimals\":2,"
        + "\"direction\":\"lower_better\",\"sdgGoals\":[6],\"status\":\"active\"" + extra + "}";
  }

  @Test
  void kpisBenchmarksMaturityAndSdgAlignment() throws Exception {
    String org = freshSite();
    String manager = bearerForRole("ESG_MANAGER");
    String director = bearerForRole("ESG_DIRECTOR");
    String code = "water_" + Long.toString(System.nanoTime(), 36);

    // KPI rules mirror the form: a second metric for ÷, both target fields or neither, a code made of a-z, 0-9 and _.
    send(post("/v1/analytics/kpis"), manager, kpi(code, org, "").replace("\"metricB\":\"production_output\",", "")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.metricB").exists());
    send(post("/v1/analytics/kpis"), manager, kpi(code, org, ",\"targetValue\":150")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.targetYear").exists());
    send(post("/v1/analytics/kpis"), manager, kpi("Water-1", org, "")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.code").exists());
    send(post("/v1/analytics/kpis"), manager, kpi(code, ORG_SITE_2, "")).andExpect(status().isForbidden());
    send(post("/v1/analytics/kpis"), bearerForRole("ESG_ANALYST"), kpi(code, org, "")).andExpect(status().isForbidden());

    // 1,200 m3 ÷ 1,000 units × 1,000 = 1,200 litres per unit in the history year; 200 m3 (Jan + Feb) ÷ 1,200 units = 166.67 the next.
    approvedFebruary(org, 100);
    JsonNode k = data(send(post("/v1/analytics/kpis"), manager, kpi(code, org, ",\"baselineYear\":" + Y0 + ",\"targetValue\":150,\"targetYear\":" + YEAR))
        .andExpect(status().isCreated()));
    assertThat(k.path("baselineValue").decimalValue()).isEqualByComparingTo("1200.00");
    assertThat(k.path("latest").path("year").asInt()).isEqualTo(Y1);
    assertThat(k.path("latest").path("value").decimalValue()).isEqualByComparingTo("166.67");
    // Halfway from the baseline to the target year, the line is at 675; 166.67 is better.
    assertThat(k.path("onTrack").asBoolean()).isTrue();
    assertThat(k.path("trackNote").asText()).isEqualTo("On track for " + YEAR);
    assertThat(k.path("series").toString()).contains("No approved Water consumption for " + YEAR);
    send(post("/v1/analytics/kpis"), manager, kpi(code, org, "")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.code").exists());
    JsonNode updated = data(send(put("/v1/analytics/kpis/" + k.path("id").asText()), manager, kpi(code, org, ",\"baselineYear\":" + Y0
        + ",\"targetValue\":100,\"targetYear\":" + YEAR + ",\"version\":" + k.path("version").asLong())).andExpect(status().isOk()));
    assertThat(updated.path("targetValue").decimalValue()).isEqualByComparingTo("100");

    // Benchmarks: the source document is required, the quartiles in order, a metric and year once.
    String file = data(mvc.perform(multipart("/v1/files").file(new MockMultipartFile("file", "water-benchmark.pdf", "application/pdf",
        "%PDF-1.7 benchmark".getBytes(StandardCharsets.US_ASCII))).param("resourceType", "benchmark_dataset").header("Authorization", director))
        .andExpect(status().isCreated())).path("id").asText();
    String name = "Manufacturing water survey " + code;
    String values = "[{\"metricKey\":\"water_consumption_m3\",\"dataYear\":" + Y0 + ",\"p25\":1000,\"median\":1500,\"p75\":2000,\"sampleSize\":42}]";
    String dataset = "{\"name\":\"" + name + "\",\"publisher\":\"Manufacturers Association of Nigeria\",\"publicationYear\":" + Y1 + ",\"sectorCode\":\"C\","
        + "\"countryCode\":\"NG\",\"sourceFileId\":\"" + file + "\",\"status\":\"active\",\"values\":" + values + "}";
    send(post("/v1/analytics/benchmark-datasets"), manager, dataset).andExpect(status().isForbidden());
    send(post("/v1/analytics/benchmark-datasets"), director, dataset.replace(file, UUID.randomUUID().toString())).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.sourceFileId").exists());
    send(post("/v1/analytics/benchmark-datasets"), director, dataset.replace("\"median\":1500", "\"median\":900")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['values[0].median']").exists());
    send(post("/v1/analytics/benchmark-datasets"), director, dataset.replace(values, "[" + values.substring(1, values.length() - 1) + ","
        + values.substring(1))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors['values[1].metricKey']").exists());
    send(post("/v1/analytics/benchmark-datasets"), director, dataset.replace(values, "[]")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.values").exists());
    JsonNode ds = data(send(post("/v1/analytics/benchmark-datasets"), director, dataset).andExpect(status().isCreated()));
    assertThat(ds.path("sourceFileName").asText()).isEqualTo("water-benchmark.pdf");
    assertThat(ds.path("values").get(0).path("unit").asText()).isEqualTo("m3");
    send(post("/v1/analytics/benchmark-datasets"), director, dataset).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());

    // The site's 1,200 m3 sits between P25 and the median: second-best quarter (lower is better). Named and dated.
    JsonNode comparisons = read("/v1/analytics/benchmarks?year=" + Y0 + "&metric=water_consumption_m3", manager);
    JsonNode c = null;
    for (JsonNode x : comparisons) {
      if (name.equals(x.path("datasetName").asText())) {
        c = x;
      }
    }
    assertThat(c).isNotNull();
    assertThat(c.path("publisher").asText()).isEqualTo("Manufacturers Association of Nigeria");
    assertThat(c.path("comparable").asBoolean()).as("an absolute total compares poorly across sizes").isFalse();
    boolean placed = false;
    for (JsonNode o : c.path("organisations")) {
      if (org.equals(o.path("orgId").asText())) {
        assertThat(o.path("value").decimalValue()).isEqualByComparingTo("1200");
        assertThat(o.path("band").asInt()).isEqualTo(2);
        placed = true;
      }
    }
    assertThat(placed).isTrue();
    // A later year uses the latest figures the dataset has.
    assertThat(read("/v1/analytics/benchmarks?year=" + Y1 + "&metric=water_consumption_m3", manager).toString()).contains(name);
    JsonNode internal = read("/v1/analytics/benchmarks/internal?metric=water_consumption_m3&year=" + Y0, manager);
    assertThat(internal.path("rows").toString()).contains(org);
    mvc.perform(get("/v1/analytics/benchmarks/internal?metric=unicorns").header("Authorization", manager)).andExpect(status().isBadRequest());

    // Maturity (this year, now the KPI exists): six dimensions, each with its evidence; snapshots by analytics:create.
    JsonNode m = read("/v1/analytics/maturity?year=" + YEAR, manager);
    assertThat(m.path("dimensions").size()).isEqualTo(6);
    assertThat(m.path("overall").decimalValue()).isBetween(java.math.BigDecimal.ONE, java.math.BigDecimal.valueOf(5));
    assertThat(m.path("dimensions").get(4).path("evidence").toString()).contains("A KPI with a target tracked");
    send(post("/v1/analytics/maturity/assessments"), bearerForRole("ESG_ANALYST"), "{\"year\":" + YEAR + "}").andExpect(status().isForbidden());
    JsonNode snap = data(send(post("/v1/analytics/maturity/assessments"), manager, "{\"year\":" + YEAR + ",\"note\":\"Board pack\"}")
        .andExpect(status().isCreated()));
    assertThat(snap.path("overall").decimalValue()).isEqualByComparingTo(m.path("overall").decimalValue());
    assertThat(read("/v1/analytics/maturity/assessments", manager).toString()).contains(snap.path("id").asText());

    // SDG 6: water is evidenced and improving (1,200 → 200 m3); the KPI tagged with goal 6 is listed.
    JsonNode goal6 = read("/v1/analytics/sdg-alignment?year=" + Y1, manager).path("goals").get(5);
    assertThat(goal6.path("goal").asInt()).isEqualTo(6);
    assertThat(goal6.path("metrics").toString()).contains("water_consumption_m3");
    for (JsonNode x : goal6.path("metrics")) {
      if ("water_consumption_m3".equals(x.path("metricKey").asText())) {
        assertThat(x.path("evidenced").asBoolean()).isTrue();
        assertThat(x.path("improving").asBoolean()).isTrue();
      }
    }
    assertThat(goal6.path("score").asInt()).isPositive();
    assertThat(goal6.path("kpis").toString()).contains(k.path("id").asText());
    JsonNode set = data(send(put("/v1/analytics/sdg-priorities?year=" + Y1), manager, "{\"goal\":6,\"rationale\":\"Water-stressed sites\"}")
        .andExpect(status().isOk()));
    JsonNode g6 = set.path("goals").get(5);
    assertThat(g6.path("priority").asBoolean()).isTrue();
    send(put("/v1/analytics/sdg-priorities"), manager, "{\"goal\":6,\"rationale\":\"Again\"}").andExpect(status().isBadRequest());
    send(put("/v1/analytics/sdg-priorities"), manager, "{\"goal\":6,\"rationale\":\"Water-stressed sites in the north\",\"version\":"
        + g6.path("priorityVersion").asLong() + "}").andExpect(status().isOk());
    send(put("/v1/analytics/sdg-priorities"), manager, "{\"goal\":18,\"rationale\":\"x\"}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.goal").exists());
    assertThat(data(send(delete("/v1/analytics/sdg-priorities/6"), manager, "").andExpect(status().isOk())).path("goals").get(5).path("priority").asBoolean())
        .isFalse();

    // Links: a KPI with a target evidences GRI 3-3 (from the year it is set up); the dashboard card.
    for (JsonNode r : read("/v1/compliance/gaps?framework=GRI&year=" + YEAR, bearerForRole("COMPLIANCE_OFFICER")).path("requirements")) {
      if ("3-3".equals(r.path("code").asText())) {
        assertThat(r.path("status").asText()).isNotEqualTo("missing");
      }
    }
    JsonNode dash = read("/v1/dashboard/executive", director);
    assertThat(dash.path("analytics").path("maturity").isNumber()).isTrue();
    JsonNode board = read("/v1/dashboard/executive", bearerForRole("BOARD_MEMBER")).path("analytics");
    assertThat(board.isMissingNode() || board.isNull()).as("no analytics:read").isTrue();

    // Report variables: the KPI by its code, the maturity level, the flag counts.
    String templateId = read("/v1/reports/templates", manager).get(0).path("id").asText();
    JsonNode report = data(send(post("/v1/reports"), manager, "{\"templateId\":\"" + templateId + "\",\"title\":\"Analytics " + code
        + "\",\"reportingPeriodStart\":\"" + YEAR + "-01-01\",\"reportingPeriodEnd\":\"" + YEAR + "-12-31\"}").andExpect(status().isCreated()));
    String section = "{\"title\":\"Analytics\",\"type\":\"data\",\"content\":\"KPI {{kpi:" + code + "}}; maturity {{analytics:maturity_level}}; "
        + "open flags {{analytics:anomalies_open}}; {{analytics:variance_explanations}} {{kpi:no_such_kpi}}\"}";
    send(put("/v1/reports/" + report.path("id").asText() + "/sections"), manager, "{\"version\":" + report.path("version").asLong() + ",\"sections\":["
        + section + "]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors['sections[0].content']").value("Unknown variable: {{kpi:no_such_kpi}}"));
    report = data(send(put("/v1/reports/" + report.path("id").asText() + "/sections"), manager, "{\"version\":" + report.path("version").asLong()
        + ",\"sections\":[" + section.replace(" {{kpi:no_such_kpi}}", "") + "]}").andExpect(status().isOk()));
    JsonNode snapshot = report.path("snapshot");
    assertThat(snapshot.path("kpi:" + code).path("status").asText()).as("no value for this year yet").isEqualTo("missing");
    assertThat(snapshot.path("kpi:" + code).path("basis").asText()).isEqualTo("the KPI has no value for " + YEAR);
    assertThat(snapshot.path("analytics:maturity_level").path("display").asText()).matches("[1-5] — .+");
    assertThat(snapshot.path("analytics:anomalies_open").path("status").asText()).isEqualTo("ok");
    assertThat(snapshot.has("analytics:variance_explanations")).isTrue();

    // Archived and deleted.
    send(delete("/v1/analytics/kpis/" + k.path("id").asText()), bearerForRole("ESG_ANALYST"), "").andExpect(status().isForbidden());
    mvc.perform(delete("/v1/analytics/kpis/" + k.path("id").asText()).header("Authorization", manager)).andExpect(status().isNoContent());
    mvc.perform(get("/v1/analytics/kpis/" + k.path("id").asText()).header("Authorization", manager)).andExpect(status().isNotFound());
    mvc.perform(delete("/v1/analytics/benchmark-datasets/" + ds.path("id").asText()).header("Authorization", director)).andExpect(status().isNoContent());
  }

  @Test
  void accessFollowsTheRoleMatrixAndTheTenant() throws Exception {
    // Readers: director, manager, analyst, auditor. Not: data collectors, investors, board members.
    for (String role : List.of("ESG_DIRECTOR", "ESG_MANAGER", "ESG_ANALYST", "AUDITOR")) {
      mvc.perform(get("/v1/analytics/anomalies").header("Authorization", bearerForRole(role))).andExpect(status().isOk());
    }
    for (String role : List.of("DATA_COLLECTOR", "INVESTOR_VIEWER", "BOARD_MEMBER")) {
      mvc.perform(get("/v1/analytics/overview").header("Authorization", bearerForRole(role))).andExpect(status().isForbidden());
    }
    send(post("/v1/analytics/anomalies/scan"), bearerForRole("AUDITOR"), "{}").andExpect(status().isForbidden());
    mvc.perform(get("/v1/analytics/anomalies?status=nope").header("Authorization", bearerForRole("ESG_DIRECTOR"))).andExpect(status().isBadRequest());
    mvc.perform(get("/v1/analytics/maturity?year=1999").header("Authorization", bearerForRole("ESG_DIRECTOR"))).andExpect(status().isBadRequest());

    // Another tenant sees none of tenant A's flags.
    String org = freshSite();
    approvedFebruary(org, 160);
    String id = read("/v1/analytics/anomalies?year=" + Y1 + "&orgId=" + org, bearerForRole("ESG_DIRECTOR")).get(0).path("id").asText();
    String tenantB = bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B);
    // (Tenant B's Starter plan has no analytics module at all; the RLS suite proves the rows are invisible to it.)
    int s = mvc.perform(get("/v1/analytics/anomalies/" + id).header("Authorization", tenantB)).andReturn().getResponse().getStatus();
    assertThat(s).isIn(403, 404);
    // Nor does a manager scoped elsewhere: Site 2's data isn't the Site 1 manager's to see.
    assertThat(read("/v1/analytics/anomalies?orgId=" + ORG_SITE_2, bearerForRole("ESG_MANAGER")).size()).isZero();
  }

  // ---------------------------------------------------------------- helpers

  private static long inbox(String userId, String type) throws Exception {
    return Long.parseLong(ownerValue("select count(*) from notifications.notification_queue where user_id = '" + userId + "' and notification_type = '" + type + "'"));
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
