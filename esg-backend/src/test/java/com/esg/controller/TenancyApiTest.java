package com.esg.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** WP-14: organisations, reporting periods, tenant settings, platform tenants, admin account recovery. */
class TenancyApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;

  private String admin() {
    return bearerForRole("TENANT_ADMIN");
  }

  private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String auth, String body)
      throws Exception {
    return mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode data(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
  }

  // ---------------------------------------------------------------- organisations

  @Test
  void organisationCreateAllFieldsRoundTripsAndUpdates() throws Exception {
    String body = "{\"name\":\"Warri Depot\",\"parentOrgId\":\"" + ORG_SITE_2 + "\",\"orgType\":\"facility\",\"countryCode\":\"NG\","
        + "\"stateRegion\":\"Delta\",\"address\":\"1 Refinery Rd\",\"latitude\":5.5167,\"longitude\":5.75}";
    JsonNode created = data(send(post("/v1/organisations"), admin(), body).andExpect(status().isCreated()));
    String id = created.path("id").asText();

    mvc.perform(get("/v1/organisations/" + id).header("Authorization", admin()))
        .andExpect(jsonPath("$.data.name").value("Warri Depot"))
        .andExpect(jsonPath("$.data.parentOrgId").value(ORG_SITE_2))
        .andExpect(jsonPath("$.data.orgType").value("facility"))
        .andExpect(jsonPath("$.data.countryCode").value("NG"))
        .andExpect(jsonPath("$.data.stateRegion").value("Delta"))
        .andExpect(jsonPath("$.data.address").value("1 Refinery Rd"))
        .andExpect(jsonPath("$.data.latitude").value(5.5167))
        .andExpect(jsonPath("$.data.longitude").value(5.75));

    String update = "{\"name\":\"Warri Terminal\",\"parentOrgId\":\"" + ORG_HQ + "\",\"orgType\":\"site\",\"countryCode\":\"NG\","
        + "\"stateRegion\":\"Delta\",\"address\":\"2 Port Rd\",\"latitude\":5.52,\"longitude\":5.76,\"version\":" + created.path("version").asLong() + "}";
    send(put("/v1/organisations/" + id), admin(), update)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Warri Terminal"))
        .andExpect(jsonPath("$.data.parentOrgId").value(ORG_HQ))
        .andExpect(jsonPath("$.data.orgType").value("site"))
        .andExpect(jsonPath("$.data.address").value("2 Port Rd"));
    // Stale version.
    send(put("/v1/organisations/" + id), admin(), update).andExpect(status().isConflict());

    // A leaf with no data can be removed, then it's gone.
    mvc.perform(delete("/v1/organisations/" + id).header("Authorization", admin())).andExpect(status().isOk());
    mvc.perform(get("/v1/organisations/" + id).header("Authorization", admin())).andExpect(status().isNotFound());
  }

  @Test
  void organisationCannotBeMovedUnderItsOwnDescendant() throws Exception {
    JsonNode hq = data(mvc.perform(get("/v1/organisations/" + ORG_HQ).header("Authorization", admin())));
    String body = "{\"name\":\"A HQ\",\"parentOrgId\":\"" + ORG_SITE_1 + "\",\"orgType\":\"headquarters\",\"version\":" + hq.path("version").asLong() + "}";
    send(put("/v1/organisations/" + ORG_HQ), admin(), body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.parentOrgId").exists());
  }

  @Test
  void organisationInUseCannotBeDeleted() throws Exception {
    mvc.perform(delete("/v1/organisations/" + ORG_HQ).header("Authorization", admin()))
        .andExpect(status().isConflict());
  }

  @Test
  void organisationValidationMirrorsTheRules() throws Exception {
    send(post("/v1/organisations"), admin(), "{\"name\":\"X\",\"orgType\":\"castle\",\"parentOrgId\":\"" + ORG_HQ + "\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.orgType").exists());
    send(post("/v1/organisations"), admin(), "{\"name\":\"X\",\"orgType\":\"site\",\"latitude\":91,\"parentOrgId\":\"" + ORG_HQ + "\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.latitude").exists());
    send(post("/v1/organisations"), admin(), "{\"name\":\"\",\"orgType\":\"site\",\"countryCode\":\"nigeria\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists()).andExpect(jsonPath("$.errors.countryCode").exists());
  }

  @Test
  void orgScopedUsersSeeOnlyTheirSubtree() throws Exception {
    mvc.perform(get("/v1/organisations").header("Authorization", bearerForRole("ESG_ANALYST")))
        .andExpect(jsonPath("$.data[*].id", everyItem(is(ORG_SITE_1))));
    mvc.perform(get("/v1/organisations/" + ORG_HQ).header("Authorization", bearerForRole("ESG_ANALYST")))
        .andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------- reporting periods

  @Test
  void lockedPeriodRejectsDataAndUnlockAllowsIt() throws Exception {
    JsonNode period = data(send(post("/v1/reporting-periods"), admin(),
        "{\"name\":\"Q3 2027\",\"periodType\":\"quarterly\",\"startDate\":\"2027-07-01\",\"endDate\":\"2027-09-30\"}")
        .andExpect(status().isCreated()));
    String id = period.path("id").asText();
    String dataPoint = "{\"orgId\":\"" + ORG_HQ + "\",\"category\":\"E\",\"subCategory\":\"Energy\",\"metricKey\":\"electricity_kwh\","
        + "\"numericValue\":1,\"unit\":\"kWh\",\"reportingPeriodStart\":\"2027-08-01\",\"reportingPeriodEnd\":\"2027-08-31\"}";

    // Only roles with reporting_periods:approve can lock.
    mvc.perform(post("/v1/reporting-periods/" + id + "/lock").header("Authorization", bearerForRole("DATA_COLLECTOR")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/v1/reporting-periods/" + id + "/lock").header("Authorization", bearerForRole("ESG_DIRECTOR")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("locked"));

    send(post("/v1/data/points"), bearerForRole("ESG_DIRECTOR"), dataPoint)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Q3 2027")));

    mvc.perform(post("/v1/reporting-periods/" + id + "/unlock").header("Authorization", bearerForRole("ESG_DIRECTOR")))
        .andExpect(jsonPath("$.data.status").value("open"));
    send(post("/v1/data/points"), bearerForRole("ESG_DIRECTOR"), dataPoint).andExpect(status().isCreated());
  }

  @Test
  void databaseTriggerBlocksWritesIntoLockedPeriodsEvenWithoutTheApi() throws SQLException {
    // FY2020 is locked in the fixture. Write directly as the application role, bypassing the service check.
    try (Connection app = PostgresTestDatabase.appConnection(); Statement st = app.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false), "
          + "set_config('app.current_user', '" + USER_A + "', false)");
      assertThatThrownBy(() -> st.executeUpdate("insert into carbon.emission_records (org_id, scope, source_activity, activity_data, "
          + "activity_unit, co2e_tonnes, reporting_period_start, reporting_period_end) values ('" + ORG_HQ + "', 'scope_1', 'diesel', 1, "
          + "'L', 0.00268, '2020-03-01', '2020-03-31')"))
          .isInstanceOf(SQLException.class)
          .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("P0L01"));
    }
  }

  @Test
  void periodValidation() throws Exception {
    send(post("/v1/reporting-periods"), admin(), "{\"name\":\"Bad\",\"periodType\":\"quarterly\",\"startDate\":\"2028-06-30\",\"endDate\":\"2028-04-01\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.endDate").exists());
    send(post("/v1/reporting-periods"), admin(), "{\"name\":\"Overlap\",\"periodType\":\"quarterly\",\"startDate\":\"2026-02-01\",\"endDate\":\"2026-04-30\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.startDate").exists());
    send(post("/v1/reporting-periods"), admin(), "{\"name\":\"X\",\"periodType\":\"weekly\",\"startDate\":\"2029-01-01\",\"endDate\":\"2029-01-07\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.periodType").exists());
  }

  // ---------------------------------------------------------------- settings

  @Test
  void settingsRoundTripAndValidation() throws Exception {
    JsonNode current = data(mvc.perform(get("/v1/settings").header("Authorization", admin())).andExpect(status().isOk()));
    long version = current.path("version").asLong();
    String body = "{\"baseCurrency\":\"USD\",\"fiscalYearStartMonth\":4,\"reportingTimezone\":\"Africa/Nairobi\","
        + "\"defaultFrameworkCodes\":[\"GRI\",\"ISSB_S2\"],\"version\":" + version + "}";
    send(put("/v1/settings"), admin(), body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.baseCurrency").value("USD"))
        .andExpect(jsonPath("$.data.fiscalYearStartMonth").value(4))
        .andExpect(jsonPath("$.data.reportingTimezone").value("Africa/Nairobi"))
        .andExpect(jsonPath("$.data.defaultFrameworkCodes", hasItem("ISSB_S2")));
    send(put("/v1/settings"), admin(), body).andExpect(status().isConflict());

    long next = data(mvc.perform(get("/v1/settings").header("Authorization", admin()))).path("version").asLong();
    send(put("/v1/settings"), admin(), "{\"baseCurrency\":\"BTC\",\"fiscalYearStartMonth\":13,\"reportingTimezone\":\"x\","
        + "\"defaultFrameworkCodes\":[],\"version\":" + next + "}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.baseCurrency").exists())
        .andExpect(jsonPath("$.errors.fiscalYearStartMonth").exists());
    send(put("/v1/settings"), admin(), "{\"baseCurrency\":\"NGN\",\"fiscalYearStartMonth\":1,\"reportingTimezone\":\"Africa/Lagos\","
        + "\"defaultFrameworkCodes\":[\"MADE_UP\"],\"version\":" + next + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.defaultFrameworkCodes").exists());

    // Directors can read but not change settings.
    send(put("/v1/settings"), bearerForRole("ESG_DIRECTOR"), body).andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------- platform tenants

  @Test
  void superAdminCreatesTenantWithFirstAdmin() throws Exception {
    String slug = "acme-" + UUID.randomUUID().toString().substring(0, 6);
    String email = slug + "@fixture.test";
    String body = "{\"name\":\"Acme Bank\",\"slug\":\"" + slug + "\",\"countryCode\":\"NG\",\"industrySector\":\"Banking\","
        + "\"subscriptionTier\":\"professional\",\"billingCurrency\":\"NGN\",\"maxUsers\":25,"
        + "\"admin\":{\"email\":\"" + email + "\",\"firstName\":\"Bola\",\"lastName\":\"Ade\",\"password\":\"Str0ng!Passw0rd\"}}";

    send(post("/v1/platform/tenants"), admin(), body).andExpect(status().isForbidden());
    JsonNode tenant = data(send(post("/v1/platform/tenants"), bearerForRole("SUPER_ADMIN"), body).andExpect(status().isCreated()));
    assertThat(tenant.path("users").asLong()).isEqualTo(1);

    // The first admin can sign in to the new tenant (TENANT_ADMIN requires MFA, so the password step returns a challenge).
    send(post("/v1/auth/login"), "", "{\"email\":\"" + email + "\",\"password\":\"Str0ng!Passw0rd\"}")
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.mfaSetupRequired").value(true));

    String update = "{\"name\":\"Acme Bank Plc\",\"industrySector\":\"Banking\",\"subscriptionTier\":\"enterprise\",\"maxUsers\":50,"
        + "\"active\":true,\"version\":" + tenant.path("version").asLong() + "}";
    send(put("/v1/platform/tenants/" + tenant.path("id").asText()), bearerForRole("SUPER_ADMIN"), update)
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.subscriptionTier").value("enterprise"));
    send(put("/v1/platform/tenants/" + tenant.path("id").asText()), bearerForRole("SUPER_ADMIN"), update)
        .andExpect(status().isConflict());

    // Duplicate slug and weak admin password are rejected without creating anything.
    send(post("/v1/platform/tenants"), bearerForRole("SUPER_ADMIN"), body.replace(email, "other." + email))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.slug").exists());
    send(post("/v1/platform/tenants"), bearerForRole("SUPER_ADMIN"), body.replace("Str0ng!Passw0rd", "weak"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors['admin.password']").exists());
  }

  @Test
  void tenantFunctionsRecheckPermissionInTheDatabase() throws SQLException {
    try (Connection app = PostgresTestDatabase.appConnection(); Statement st = app.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false), "
          + "set_config('app.current_user', '" + roleUserId("TENANT_ADMIN") + "', false)");
      assertThatThrownBy(() -> st.executeQuery("select * from auth.list_tenants()"))
          .isInstanceOf(SQLException.class)
          .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("42501"));
    }
  }

  // ---------------------------------------------------------------- account recovery

  @Test
  void adminCanSetPasswordAndResetMfa() throws Exception {
    String target = roleUserId("BOARD_MEMBER");
    send(post("/v1/admin/users/" + target + "/password"), admin(), "{\"password\":\"weak\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.password").exists());
    send(post("/v1/admin/users/" + target + "/password"), admin(), "{\"password\":\"N3w!Password2026\"}")
        .andExpect(status().isOk());
    send(post("/v1/auth/login"), "", "{\"email\":\"board_member@fixture.test\",\"password\":\"N3w!Password2026\"}")
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isNotEmpty());

    mvc.perform(post("/v1/admin/users/" + target + "/reset-mfa").header("Authorization", admin()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.mfaEnabled").value(false));
    mvc.perform(post("/v1/admin/users/" + target + "/reset-mfa").header("Authorization", bearerForRole("AUDITOR")))
        .andExpect(status().isForbidden());
  }
}
