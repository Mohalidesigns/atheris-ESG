package com.esg.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Data point create/read against the metric catalogue (WP-16/17): round trip and the rules the UI mirrors. */
class DataPointApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;

  private static String body(String metric, String extra) {
    return "{\"orgId\":\"" + ORG_HQ + "\",\"metricKey\":\"" + metric + "\",\"reportingPeriodStart\":\"2026-07-01\","
        + "\"reportingPeriodEnd\":\"2026-09-30\"" + extra + "}";
  }

  @Test
  void createRoundTripsAndTakesCategoryAndUnitFromTheCatalogue() throws Exception {
    String analyst = bearerForRole("ESG_DIRECTOR");
    String id = json.readTree(mvc.perform(post("/v1/data/points").header("Authorization", analyst)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("water_withdrawal_m3", ",\"numericValue\":12450.5,\"subCategory\":\"Borehole\"")))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString()).path("data").path("id").asText();

    mvc.perform(get("/v1/data/points/" + id).header("Authorization", analyst))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.orgId").value(ORG_HQ))
        .andExpect(jsonPath("$.data.metricKey").value("water_withdrawal_m3"))
        .andExpect(jsonPath("$.data.category").value("E"))
        .andExpect(jsonPath("$.data.subCategory").value("Borehole"))
        .andExpect(jsonPath("$.data.unit").value("m3"))
        .andExpect(jsonPath("$.data.numericValue").value(12450.5))
        .andExpect(jsonPath("$.data.reportingPeriodStart").value("2026-07-01"))
        .andExpect(jsonPath("$.data.reportingPeriodEnd").value("2026-09-30"))
        .andExpect(jsonPath("$.data.validationStatus").value("pending"));
  }

  @Test
  void subCategoryDefaultsToTheMetricCategory() throws Exception {
    mvc.perform(post("/v1/data/points").header("Authorization", bearerForRole("ESG_DIRECTOR"))
            .contentType(MediaType.APPLICATION_JSON).content(body("board_size", ",\"numericValue\":9")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.category").value("G"))
        .andExpect(jsonPath("$.data.subCategory").value("Board"))
        .andExpect(jsonPath("$.data.unit").value("count"));
  }

  static Stream<Arguments> invalid() {
    return Stream.of(
        Arguments.of("metricKey", body("made_up_metric", ",\"numericValue\":1")),
        Arguments.of("unit", body("electricity_kwh", ",\"numericValue\":1,\"unit\":\"MWh\"")),
        Arguments.of("category", body("electricity_kwh", ",\"numericValue\":1,\"category\":\"S\"")),
        Arguments.of("category", body("electricity_kwh", ",\"numericValue\":1,\"category\":\"X\"")),
        Arguments.of("numericValue", body("electricity_kwh", "")),
        Arguments.of("numericValue", body("electricity_kwh", ",\"numericValue\":-5")),
        Arguments.of("numericValue", body("employees_total", ",\"numericValue\":10.5")),
        Arguments.of("numericValue", body("board_female_pct", ",\"numericValue\":101")),
        Arguments.of("numericValue", body("electricity_kwh", ",\"numericValue\":1.1234567")),
        Arguments.of("subCategory", body("electricity_kwh", ",\"numericValue\":1,\"subCategory\":\"" + "x".repeat(101) + "\"")),
        Arguments.of("reportingPeriodEnd", "{\"orgId\":\"" + ORG_HQ + "\",\"metricKey\":\"electricity_kwh\",\"numericValue\":1,"
            + "\"reportingPeriodStart\":\"2026-09-30\",\"reportingPeriodEnd\":\"2026-07-01\"}"),
        Arguments.of("orgId", "{\"metricKey\":\"electricity_kwh\",\"numericValue\":1,\"reportingPeriodStart\":\"2026-07-01\","
            + "\"reportingPeriodEnd\":\"2026-09-30\"}"));
  }

  @ParameterizedTest(name = "{0}: {1}")
  @MethodSource("invalid")
  void rulesAreReportedOnTheField(String field, String body) throws Exception {
    mvc.perform(post("/v1/data/points").header("Authorization", bearerForRole("ESG_DIRECTOR"))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors." + field).exists());
  }

  @Test
  void listSupportsFiltersPagingAndSorting() throws Exception {
    mvc.perform(get("/v1/data/points?category=E&page=0&size=2&sort=createdAt,desc").header("Authorization", bearerForRole("AUDITOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.size").value(2))
        .andExpect(jsonPath("$.data.content[*].category", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("E"))));
    mvc.perform(get("/v1/data/points?sort=notAField,asc").header("Authorization", bearerForRole("AUDITOR")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.sort").exists());
    mvc.perform(get("/v1/carbon/emissions?sort=tenantId,asc").header("Authorization", bearerForRole("AUDITOR")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.sort").exists());
  }
}
