package com.esg.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;

/** WP-17 reference data: catalogues, sourced factors with the lookup hierarchy, frameworks and mappings. */
class ReferenceDataTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;

  private JsonNode data(String body) throws Exception {
    return json.readTree(body).path("data");
  }

  private JsonNode resolve(String role, String query) throws Exception {
    return data(mvc.perform(get("/v1/carbon/factors/resolve?" + query).header("Authorization", bearerForRole(role)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  // ---------------------------------------------------------------- golden values (WP-17 acceptance)

  @Test
  void goldenNigeriaGridElectricity() throws Exception {
    JsonNode r = resolve("ESG_ANALYST", "category=electricity_grid&country=NG&date=2026-06-30&activityData=10000");
    assertThat(r.path("level").asText()).isEqualTo("country");
    assertThat(new BigDecimal(r.path("factor").path("factorValue").asText())).isEqualByComparingTo("0.43");
    assertThat(new BigDecimal(r.path("co2eTonnes").asText())).isEqualByComparingTo("4.3");
  }

  @Test
  void goldenNigeriaDieselGenerator() throws Exception {
    JsonNode r = resolve("ESG_ANALYST", "category=diesel_generator&country=NG&date=2026-06-30&activityData=1000");
    assertThat(r.path("level").asText()).as("the Nigerian factor beats the global IPCC default").isEqualTo("country");
    assertThat(new BigDecimal(r.path("co2eTonnes").asText())).isEqualByComparingTo("2.68");
  }

  @Test
  void calculatorKeepsSixDecimals() {
    assertThat(EmissionCalculator.co2eTonnes(new BigDecimal("1234.5"), new BigDecimal("2.6856"))).isEqualByComparingTo("3.315373");
  }

  // ---------------------------------------------------------------- lookup hierarchy

  @ParameterizedTest(name = "{0} in {1} → {2} ({3})")
  @CsvSource({
      "petrol_generator, GH, global, 2.2801",
      "diesel_generator, KE, global, 2.6856",
      "air_travel_domestic, GH, region, 0.255",
      "electricity_grid, ZA, country, 0.928",
      "lpg, , global, 2.9873"})
  void fallsBackFromCountryToRegionToGlobal(String category, String country, String level, String value) throws Exception {
    JsonNode r = resolve("AUDITOR", "category=" + category + (country == null ? "" : "&country=" + country) + "&date=2026-06-30");
    assertThat(r.path("level").asText()).isEqualTo(level);
    assertThat(new BigDecimal(r.path("factor").path("factorValue").asText())).isEqualByComparingTo(value);
  }

  @Test
  void factorsAreOnlyUsedInsideTheirValidityPeriod() throws Exception {
    mvc.perform(get("/v1/carbon/factors/resolve?category=electricity_grid&country=NG&date=2023-06-30")
            .header("Authorization", bearerForRole("AUDITOR")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("electricity_grid")));
  }

  @Test
  void tenantCustomFactorWinsForThatTenantOnly() throws Exception {
    String body = "{\"region\":\"NG\",\"category\":\"electricity_grid\",\"subCategory\":\"Captive gas plant\",\"factorValue\":0.5,"
        + "\"unit\":\"kgCO2e/kWh\",\"source\":\"Plant operator certificate 2026\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\"}";
    JsonNode created = data(mvc.perform(post("/v1/carbon/factors").header("Authorization", bearerForRole("ESG_DIRECTOR"))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.custom").value(true))
        .andExpect(jsonPath("$.data.verificationStatus").value("tenant_provided"))
        .andExpect(jsonPath("$.data.subCategory").value("Captive gas plant"))
        .andExpect(jsonPath("$.data.validTo").value("2026-12-31"))
        .andReturn().getResponse().getContentAsString());
    try {
      JsonNode mine = resolve("ESG_ANALYST", "category=electricity_grid&country=NG&date=2026-06-30");
      assertThat(mine.path("level").asText()).isEqualTo("tenant_custom");
      assertThat(mine.path("factor").path("id").asText()).isEqualTo(created.path("id").asText());

      String otherTenant = bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B);
      JsonNode theirs = data(mvc.perform(get("/v1/carbon/factors/resolve?category=electricity_grid&country=NG&date=2026-06-30")
          .header("Authorization", otherTenant)).andReturn().getResponse().getContentAsString());
      assertThat(theirs.path("level").asText()).isEqualTo("country");

      // Outside its validity the platform factor applies again.
      assertThat(resolve("ESG_ANALYST", "category=electricity_grid&country=NG&date=2027-03-31").path("level").asText())
          .isEqualTo("country");
    } finally {
      asOwnerInTenantA("delete from carbon.emission_factors where id = '" + created.path("id").asText() + "'");
    }
  }

  @Test
  void customFactorValidationAndPermissions() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    mvc.perform(post("/v1/carbon/factors").header("Authorization", director).contentType(MediaType.APPLICATION_JSON)
            .content("{\"region\":\"Lagos\",\"category\":\"Diesel Gen\",\"factorValue\":-1,\"unit\":\"\",\"source\":\"\","
                + "\"validFrom\":\"2026-01-01\",\"validTo\":\"2025-01-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.region").exists())
        .andExpect(jsonPath("$.errors.category").exists())
        .andExpect(jsonPath("$.errors.factorValue").exists())
        .andExpect(jsonPath("$.errors.unit").exists())
        .andExpect(jsonPath("$.errors.source").exists());
    mvc.perform(post("/v1/carbon/factors").header("Authorization", director).contentType(MediaType.APPLICATION_JSON)
            .content("{\"region\":\"NG\",\"category\":\"x\",\"factorValue\":1,\"unit\":\"kg\",\"source\":\"s\","
                + "\"validFrom\":\"2026-01-01\",\"validTo\":\"2025-01-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.validTo").exists());
    mvc.perform(post("/v1/carbon/factors").header("Authorization", bearerForRole("ESG_ANALYST")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"region\":\"NG\",\"category\":\"x\",\"factorValue\":1,\"unit\":\"kg\",\"source\":\"s\",\"validFrom\":\"2026-01-01\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void platformFactorsAreReadOnlyAndUsedCustomFactorsKeepTheirValue() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    JsonNode ng = resolve("ESG_DIRECTOR", "category=electricity_grid&country=NG&date=2026-06-30").path("factor");
    mvc.perform(put("/v1/carbon/factors/" + ng.path("id").asText()).header("Authorization", director)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"region\":\"NG\",\"category\":\"electricity_grid\",\"factorValue\":0.1,\"unit\":\"kgCO2e/kWh\","
                + "\"source\":\"x\",\"validFrom\":\"2024-01-01\",\"version\":0}"))
        .andExpect(status().isForbidden());

    String body = "{\"region\":\"NG\",\"category\":\"test_fuel\",\"factorValue\":1.5,\"unit\":\"kgCO2e/litre\","
        + "\"source\":\"Lab test\",\"validFrom\":\"2026-01-01\"}";
    JsonNode f = data(mvc.perform(post("/v1/carbon/factors").header("Authorization", director)
        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString());
    String id = f.path("id").asText();
    try {
      mvc.perform(post("/v1/carbon/emissions").header("Authorization", director).contentType(MediaType.APPLICATION_JSON)
              .content("{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"test_fuel\",\"activityData\":100,"
                  + "\"activityUnit\":\"litre\",\"emissionFactorId\":\"" + id + "\",\"reportingPeriodStart\":\"2026-05-01\","
                  + "\"reportingPeriodEnd\":\"2026-05-31\"}"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.data.co2eTonnes").value(0.15));
      String changedValue = body.replace("1.5", "1.6").replace("}", ",\"version\":" + f.path("version").asLong() + "}");
      mvc.perform(put("/v1/carbon/factors/" + id).header("Authorization", director).contentType(MediaType.APPLICATION_JSON)
              .content(changedValue))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.errorCode").value("ESG-409-FACTOR-IN-USE"));
      String endIt = body.replace("}", ",\"validTo\":\"2026-06-30\",\"version\":" + f.path("version").asLong() + "}");
      mvc.perform(put("/v1/carbon/factors/" + id).header("Authorization", director).contentType(MediaType.APPLICATION_JSON)
              .content(endIt))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.validTo").value("2026-06-30"));
    } finally {
      // The factor stays: an emission record and an immutable lineage row now reference it.
    }
  }

  // ---------------------------------------------------------------- provenance & immutability

  @Test
  void everyPlatformValueHasASourceAndADerivation() throws SQLException {
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      assertThat(count(st, "select count(*) from carbon.emission_factors where tenant_id is null "
          + "and (source is null or derivation is null)")).isZero();
      assertThat(count(st, "select count(*) from compliance.framework_requirements where source is null")).isZero();
      assertThat(count(st, "select count(*) from compliance.regulatory_frameworks where content_status = 'seeded' and source is null")).isZero();
      assertThat(count(st, "select count(*) from carbon.emission_factors where tenant_id is null "
          + "and verification_status <> 'pending_sme'")).as("nothing is marked SME-verified before the SME signs off").isZero();
    }
  }

  @Test
  void migrationRefusesToChangeAPublishedFactorValue() throws Exception {
    String script = new ClassPathResource("db/migration/R__ref_02_emission_factors.sql").getContentAsString(StandardCharsets.UTF_8);
    String edited = script.replace("('NG', 'electricity_grid', 'National Grid', 0.43,", "('NG', 'electricity_grid', 'National Grid', 0.44,");
    assertThat(edited).isNotEqualTo(script);
    try (Connection owner = PostgresTestDatabase.ownerConnection()) {
      owner.setAutoCommit(false);
      try (Statement st = owner.createStatement()) {
        assertThatThrownBy(() -> st.execute(edited))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("Add a new factor_version instead");
      } finally {
        owner.rollback();
      }
    }
  }

  private static long count(Statement st, String sql) throws SQLException {
    try (ResultSet rs = st.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    }
  }

  // ---------------------------------------------------------------- catalogues, frameworks, mappings

  @Test
  void cataloguesAreReadableByEveryRole() throws Exception {
    String investor = bearerForRole("INVESTOR_VIEWER");
    mvc.perform(get("/v1/reference/currencies").header("Authorization", investor))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(9)))
        .andExpect(jsonPath("$.data[?(@.code=='XOF')].minorUnit").value(0));
    mvc.perform(get("/v1/reference/sdgs").header("Authorization", investor)).andExpect(jsonPath("$.data", hasSize(17)));
    mvc.perform(get("/v1/reference/scope3-categories").header("Authorization", investor))
        .andExpect(jsonPath("$.data", hasSize(15)))
        .andExpect(jsonPath("$.data[14].name").value("Investments"));
    mvc.perform(get("/v1/reference/sectors").header("Authorization", investor)).andExpect(jsonPath("$.data", hasSize(21)));
    mvc.perform(get("/v1/reference/countries").header("Authorization", investor))
        .andExpect(jsonPath("$.data[?(@.code=='NG')].region").value("WEST_AFRICA"));
  }

  @Test
  void metricCatalogueCarriesUnitsTypesAndFrameworkLinks() throws Exception {
    String analyst = bearerForRole("ESG_ANALYST");
    JsonNode metrics = data(mvc.perform(get("/v1/reference/metrics").header("Authorization", analyst))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(31)))
        .andReturn().getResponse().getContentAsString());
    JsonNode scope1 = null;
    for (JsonNode m : metrics) {
      if (m.path("metricKey").asText().equals("scope1_tco2e")) {
        scope1 = m;
      }
    }
    assertThat(scope1.path("unit").asText()).isEqualTo("tCO2e");
    assertThat(scope1.path("dataType").asText()).isEqualTo("decimal");
    assertThat(scope1.path("requirements").findValuesAsText("code")).containsExactlyInAnyOrder("305-1", "S2-29(a)(i)(1)", "MT-b");
    mvc.perform(get("/v1/reference/metrics?pillar=G").header("Authorization", analyst))
        .andExpect(jsonPath("$.data[*].pillar", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("G"))));
    mvc.perform(get("/v1/reference/metrics?framework=CBN_NSBP").header("Authorization", analyst))
        .andExpect(jsonPath("$.data[*].metricKey", org.hamcrest.Matchers.containsInAnyOrder("employees_female", "women_in_management_pct")));
  }

  @Test
  void frameworksReportRequirementCountsAndContentStatus() throws Exception {
    String officer = bearerForRole("COMPLIANCE_OFFICER");
    mvc.perform(get("/v1/compliance/frameworks").header("Authorization", officer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(12)))
        .andExpect(jsonPath("$.data[?(@.code=='GRI')].requirementCount").value(61))
        .andExpect(jsonPath("$.data[?(@.code=='ISSB_S2')].requirementCount").value(23))
        .andExpect(jsonPath("$.data[?(@.code=='TCFD')].requirementCount").value(15))
        .andExpect(jsonPath("$.data[?(@.code=='CBN_NSBP')].requirementCount").value(9))
        .andExpect(jsonPath("$.data[?(@.code=='SEC_NG')].requirementCount").value(0))
        .andExpect(jsonPath("$.data[?(@.code=='SEC_NG')].contentStatus").value("pending_sme"));
  }

  @Test
  void requirementsCarryHierarchyMetricsAndCrossFrameworkMappings() throws Exception {
    String officer = bearerForRole("COMPLIANCE_OFFICER");
    JsonNode reqs = data(mvc.perform(get("/v1/compliance/frameworks/ISSB_S2/requirements").header("Authorization", officer))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    JsonNode scope1 = null;
    for (JsonNode r : reqs) {
      if (r.path("code").asText().equals("S2-29(a)(i)(1)")) {
        scope1 = r;
      }
    }
    assertThat(scope1.path("parentCode").asText()).isEqualTo("S2-29(a)");
    assertThat(scope1.path("metricKeys").findValuesAsText("")).isEmpty();
    assertThat(scope1.path("metricKeys").get(0).asText()).isEqualTo("scope1_tco2e");
    assertThat(scope1.path("mappedTo").findValuesAsText("code")).containsExactly("305-1");
    assertThat(reqs.findValuesAsText("title")).contains("Management's role in governance processes");
    mvc.perform(get("/v1/compliance/mappings?framework=TCFD").header("Authorization", officer))
        .andExpect(jsonPath("$.data", hasSize(8)));
    mvc.perform(get("/v1/compliance/mappings").header("Authorization", officer))
        .andExpect(jsonPath("$.data", hasSize(14)))
        .andExpect(jsonPath("$.data[?(@.source.code=='305-1')].relation").value("equivalent"));
    mvc.perform(get("/v1/compliance/frameworks/NOPE/requirements").header("Authorization", officer))
        .andExpect(status().isNotFound());
  }
}
