package com.esg.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** WP-24 executive dashboard: live figures, org scope, and sections gated by the module behind them. */
class DashboardApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;

  private static final int YEAR = LocalDate.now(ZoneId.of("Africa/Lagos")).getYear();

  private JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  private JsonNode dashboard(String token, String granularity) throws Exception {
    return data(mvc.perform(get("/v1/dashboard/executive?granularity=" + granularity).header("Authorization", token))
        .andExpect(status().isOk()));
  }

  private JsonNode send(String method, String url, String token, String body) throws Exception {
    var b = "POST".equals(method) ? post(url) : put(url);
    return data(mvc.perform(b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body)));
  }

  private static BigDecimal total(JsonNode trend, String period) {
    for (JsonNode p : trend) {
      if (period.equals(p.path("period").asText())) {
        return p.path("total").decimalValue();
      }
    }
    throw new AssertionError("no period " + period);
  }

  @Test
  void everyFigureComesFromLiveDataAndFollowsTheWorkflow() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    JsonNode before = dashboard(director, "year");
    assertThat(before.path("year").asInt()).isEqualTo(YEAR);
    JsonNode trend = before.path("emissions").path("trend");
    assertThat(trend).hasSize(5);
    assertThat(trend.get(0).path("period").asText()).isEqualTo(String.valueOf(YEAR - 4));
    assertThat(trend.get(4).path("period").asText()).isEqualTo(String.valueOf(YEAR));
    BigDecimal yearBefore = total(trend, String.valueOf(YEAR));

    // Approved electricity: the year's emissions grow by its record, SDG 7 counts the metric, the feed shows the approval.
    String day = YEAR + "-02-24";
    String collector = bearerForRole("DATA_COLLECTOR");
    JsonNode dp = send("POST", "/v1/data/points", collector, "{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"electricity_kwh\","
        + "\"numericValue\":10000,\"reportingPeriodStart\":\"" + day + "\",\"reportingPeriodEnd\":\"" + day + "\"}");
    String id = dp.path("id").asText();
    dp = send("PUT", "/v1/data/points/" + id + "/submit", collector, "{\"version\":" + dp.path("version").asLong() + "}");
    dp = send("PUT", "/v1/data/points/" + id + "/validate", bearerForRole("ESG_ANALYST"), "{\"version\":" + dp.path("version").asLong() + "}");
    send("PUT", "/v1/data/points/" + id + "/approve", director, "{\"version\":" + dp.path("version").asLong() + "}");

    JsonNode after = dashboard(director, "year");
    assertThat(total(after.path("emissions").path("trend"), String.valueOf(YEAR)).subtract(yearBefore))
        .isEqualByComparingTo("4.3"); // 10,000 kWh × 0.43 kgCO2e/kWh (Nigerian grid)
    assertThat(after.path("emissions").path("currentYear").decimalValue()).isEqualByComparingTo(total(after.path("emissions").path("trend"),
        String.valueOf(YEAR)));
    JsonNode sdg7 = after.path("sdg").path("goals").get(6);
    assertThat(sdg7.path("goal").asInt()).isEqualTo(7);
    assertThat(sdg7.path("evidenced").toString()).contains("Electricity consumed");
    assertThat(sdg7.path("linkedMetrics").asInt()).isEqualTo(6);
    assertThat(after.path("sdg").path("goals")).hasSize(17);
    JsonNode first = after.path("activity").get(0);
    assertThat(first.path("verb").asText()).isEqualTo("approved");
    assertThat(first.path("kind").asText()).isEqualTo("data_point");
    assertThat(first.path("title").asText()).startsWith("Electricity consumed · A Site 1");
    assertThat(first.path("link").asText()).isEqualTo("/data-hub?point=" + id);
    List<String> verbs = new ArrayList<>();
    after.path("activity").forEach(a -> verbs.add(a.path("verb").asText()));
    assertThat(verbs).contains("validated", "submitted");

    // Compliance: one score per framework with content; the active ones are averaged.
    JsonNode compliance = after.path("compliance");
    assertThat(compliance.path("frameworks").size()).isGreaterThanOrEqualTo(10);
    compliance.path("frameworks").forEach(f -> assertThat(f.path("requirements").asInt()).isPositive());

    // Quarterly: the last eight quarters, ending with the current one.
    JsonNode quarters = dashboard(director, "quarter").path("emissions").path("trend");
    assertThat(quarters).hasSize(8);
    int q = (LocalDate.now(ZoneId.of("Africa/Lagos")).getMonthValue() - 1) / 3 + 1;
    assertThat(quarters.get(7).path("period").asText()).isEqualTo(YEAR + "-Q" + q);
    assertThat(total(quarters, YEAR + "-Q1").compareTo(new BigDecimal("4.3"))).isGreaterThanOrEqualTo(0);
  }

  @Test
  void figuresFollowTheCallersOrganisationScope() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    // A Scope 1 record at HQ: tenant-wide roles see it, the analyst (scoped to Site 1) doesn't.
    String day = YEAR + "-03-17";
    send("POST", "/v1/carbon/emissions", director, "{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"diesel_generator\","
        + "\"activityData\":1000,\"activityUnit\":\"litre\",\"reportingPeriodStart\":\"" + day + "\",\"reportingPeriodEnd\":\"" + day + "\"}");
    BigDecimal all = total(dashboard(director, "year").path("emissions").path("trend"), String.valueOf(YEAR));
    BigDecimal site1 = total(dashboard(bearerForRole("ESG_ANALYST"), "year").path("emissions").path("trend"), String.valueOf(YEAR));
    assertThat(all.subtract(site1)).isGreaterThanOrEqualTo(new BigDecimal("2.68"));
  }

  @Test
  void sectionsFollowThePermissionsBehindThem() throws Exception {
    // Board members see the dashboard but not carbon, compliance or Data Hub figures; their feed is reports only.
    JsonNode board = dashboard(bearerForRole("BOARD_MEMBER"), "year");
    assertThat(board.path("emissions").isNull() || board.path("emissions").isMissingNode()).as("emissions").isTrue();
    assertThat(board.path("compliance").isNull() || board.path("compliance").isMissingNode()).as("compliance").isTrue();
    assertThat(board.path("sdg").isNull() || board.path("sdg").isMissingNode()).as("sdg").isTrue();
    assertThat(board.path("filings").isNull() || board.path("filings").isMissingNode()).as("filings").isTrue();
    board.path("activity").forEach(a -> assertThat(a.path("kind").asText()).isEqualTo("report"));
    // No dashboard permission: refused (and audited as denied).
    mvc.perform(get("/v1/dashboard/executive").header("Authorization", bearerForRole("DATA_COLLECTOR"))).andExpect(status().isForbidden());
    mvc.perform(get("/v1/dashboard/executive?granularity=month").header("Authorization", bearerForRole("ESG_DIRECTOR")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.granularity").exists());
  }

  @Test
  void upcomingFilingsAreListedSoonestFirst() throws Exception {
    String officer = bearerForRole("COMPLIANCE_OFFICER");
    LocalDate due = LocalDate.now(ZoneId.of("Africa/Lagos")).plusDays(20);
    send("POST", "/v1/compliance/deadlines", officer, "{\"framework\":\"CBN_NSBP\",\"description\":\"Dashboard filing check\",\"deadlineDate\":\""
        + due + "\",\"reportingYear\":" + YEAR + ",\"ownerUserId\":\"" + roleUserId("COMPLIANCE_OFFICER") + "\",\"reminderDays\":[14,7,1]}");
    JsonNode filings = dashboard(bearerForRole("ESG_DIRECTOR"), "year").path("filings");
    List<String> descriptions = new ArrayList<>();
    LocalDate previous = LocalDate.MIN;
    for (JsonNode f : filings) {
      descriptions.add(f.path("description").asText());
      LocalDate d = LocalDate.parse(f.path("deadlineDate").asText());
      assertThat(d).isAfterOrEqualTo(previous);
      previous = d;
    }
    assertThat(descriptions).contains("Dashboard filing check");
  }
}
