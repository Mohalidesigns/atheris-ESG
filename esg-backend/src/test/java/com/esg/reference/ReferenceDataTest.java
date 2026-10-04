package com.esg.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
        .andExpect(jsonPath("$.detail").value(containsString("electricity_grid")));
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
        .andExpect(jsonPath("$.errors.source").exists());
    // F11: an activity factor needs its unit (kgCO2e per unit); checked once the fields are well-formed.
    mvc.perform(post("/v1/carbon/factors").header("Authorization", director).contentType(MediaType.APPLICATION_JSON)
            .content("{\"region\":\"NG\",\"category\":\"diesel_generator\",\"factorValue\":1,\"unit\":\"\",\"source\":\"s\","
                + "\"validFrom\":\"2026-01-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.unit").value("is required"));
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
        + "\"source\":\"Lab test\",\"validFrom\":\"2026-01-01\",\"activityLabel\":\"Test fuel\",\"activityScope\":\"scope_1\"}";
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
        .andExpect(jsonPath("$.data", hasSize(33)))
        .andReturn().getResponse().getContentAsString());
    JsonNode scope1 = null;
    for (JsonNode m : metrics) {
      if (m.path("metricKey").asText().equals("scope1_tco2e")) {
        scope1 = m;
      }
    }
    assertThat(scope1.path("unit").asText()).isEqualTo("tCO2e");
    assertThat(scope1.path("dataType").asText()).isEqualTo("decimal");
    // Seeded links plus the bundled content packs' (Phase 3), e.g. SASB EM-EP-110a.1.
    assertThat(scope1.path("requirements").findValuesAsText("code")).contains("305-1", "S2-29(a)(i)(1)", "MT-b", "EM-EP-110a.1");
    mvc.perform(get("/v1/reference/metrics?pillar=G").header("Authorization", analyst))
        .andExpect(jsonPath("$.data[*].pillar", everyItem(is("G"))));
    mvc.perform(get("/v1/reference/metrics?framework=CBN_NSBP").header("Authorization", analyst))
        .andExpect(jsonPath("$.data[*].metricKey", containsInAnyOrder("employees_female", "women_in_management_pct")));
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
        // Phase 3 (5): bundled packs (framework-content/manifest.json), pending SME verification.
        .andExpect(jsonPath("$.data[?(@.code=='NGX')].requirementCount").value(50))
        .andExpect(jsonPath("$.data[?(@.code=='SEC_NG')].requirementCount").value(35))
        .andExpect(jsonPath("$.data[?(@.code=='PENCOM')].requirementCount").value(24))
        .andExpect(jsonPath("$.data[?(@.code=='CDP')].requirementCount").value(82))
        .andExpect(jsonPath("$.data[?(@.code=='GHG_PROTOCOL')].requirementCount").value(40))
        .andExpect(jsonPath("$.data[?(@.code=='SASB')].requirementCount").value(42))
        .andExpect(jsonPath("$.data[?(@.code=='SEC_NG')].contentStatus").value("pending_sme"))
        .andExpect(jsonPath("$.data[?(@.code=='SEC_NG')].version").value("2026-09"))
        // No issued NAICOM instrument was found (D28): still no content, and it says so.
        .andExpect(jsonPath("$.data[?(@.code=='NAICOM')].requirementCount").value(0))
        .andExpect(jsonPath("$.data[?(@.code=='NAICOM')].contentStatus").value("pending_sme"));
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

  @Test
  void emissionListFiltersByScope() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    String factor = resolve("ESG_DIRECTOR", "category=diesel_generator&country=NG&date=2026-05-01").path("factor").path("id").asText();
    String id = data(mvc.perform(post("/v1/carbon/emissions").header("Authorization", director)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"diesel_generator\","
                + "\"activityData\":10,\"activityUnit\":\"litre\",\"emissionFactorId\":\"" + factor + "\","
                + "\"reportingPeriodStart\":\"2026-05-01\",\"reportingPeriodEnd\":\"2026-05-31\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText();

    mvc.perform(get("/v1/carbon/emissions?scope=scope_1&size=200").header("Authorization", director))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[*].scope", everyItem(is("scope_1"))))
        .andExpect(jsonPath("$.data.content[*].id", hasItem(id)));
    mvc.perform(get("/v1/carbon/emissions?scope=scope_2&size=200").header("Authorization", director))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[*].id", not(hasItem(id))));
  }

  @Test
  void referenceEndpointsAreNeverEmpty() throws Exception {
    // Definition of done (Phase 1, F9): every catalogue a form picks from has content.
    for (String path : java.util.List.of("sectors", "countries", "currencies", "scope3-categories", "metrics", "timezones")) {
      mvc.perform(get("/v1/reference/" + path).header("Authorization", bearerForRole("ESG_ANALYST")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }
    for (String path : java.util.List.of("activities", "regions")) {
      mvc.perform(get("/v1/carbon/factors/" + path).header("Authorization", bearerForRole("ESG_ANALYST")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }
  }
}
