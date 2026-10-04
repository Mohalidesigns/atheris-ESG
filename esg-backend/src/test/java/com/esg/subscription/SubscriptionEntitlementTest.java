package com.esg.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * WP-19 acceptance: a module the tenant isn't entitled to answers 403 on its endpoints and disappears from the
 * user's permissions and modules (which drive the menu). Tenant A is on Enterprise, tenant B on Starter.
 */
class SubscriptionEntitlementTest extends ApiTestSupport {

  private static final String A = PostgresTestDatabase.TENANT_A;
  private static final String B = PostgresTestDatabase.TENANT_B;

  private String platform() {
    return bearerForRole("SUPER_ADMIN");
  }

  private void override(String tenant, String module, String enabled) throws Exception {
    mvc.perform(put("/v1/platform/tenants/" + tenant + "/modules/" + module).header("Authorization", platform())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":" + enabled + ",\"reason\":\"WP-19 test\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void everyPermissionResourceBelongsToAModule() throws Exception {
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement();
        ResultSet rs = st.executeQuery("select string_agg(distinct p.resource, ',') from auth.permissions p "
            + "left join reference.module_resources mr on mr.resource = p.resource where mr.resource is null")) {
      rs.next();
      assertThat(rs.getString(1)).as("unmapped permission resources grant nothing; map them in R__ref_05").isNull();
    }
  }

  @Test
  void disabledModuleReturns403AndLeavesTheMenuWithTheSameToken() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    mvc.perform(get("/v1/data/points").header("Authorization", director)).andExpect(status().isOk());
    mvc.perform(get("/v1/auth/me").header("Authorization", director))
        .andExpect(jsonPath("$.data.permissions", hasItem("data_points:read")))
        .andExpect(jsonPath("$.data.modules", hasItem("data_hub")));

    override(A, "data_hub", "false");
    try {
      mvc.perform(get("/v1/data/points").header("Authorization", director)).andExpect(status().isForbidden());
      mvc.perform(get("/v1/data/points/" + HQ_DATA_POINT).header("Authorization", director))
          .andExpect(status().isForbidden());
      mvc.perform(get("/v1/auth/me").header("Authorization", director))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.permissions", not(hasItem("data_points:read"))))
          .andExpect(jsonPath("$.data.permissions", not(hasItem("data_points:create"))))
          .andExpect(jsonPath("$.data.modules", not(hasItem("data_hub"))))
          // Other modules are untouched.
          .andExpect(jsonPath("$.data.permissions", hasItem("carbon_data:read")));
      mvc.perform(get("/v1/carbon/emissions").header("Authorization", director)).andExpect(status().isOk());
    } finally {
      override(A, "data_hub", "null");
    }
    mvc.perform(get("/v1/data/points").header("Authorization", director)).andExpect(status().isOk());
  }

  @Test
  void starterPlanExcludesGrowthModulesUntilAnAddOnIsGranted() throws Exception {
    String adminB = bearer(USER_B_ADMIN, B);
    mvc.perform(get("/v1/auth/me").header("Authorization", adminB))
        .andExpect(jsonPath("$.data.modules", hasItem("data_hub")))
        .andExpect(jsonPath("$.data.modules", not(hasItem("risk"))))
        .andExpect(jsonPath("$.data.permissions", hasItem("users:read")))
        .andExpect(jsonPath("$.data.permissions", not(hasItem("risk:read"))));
    mvc.perform(get("/v1/subscription").header("Authorization", adminB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.plan").value("starter"))
        .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'platform')].source").value("platform"))
        .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'carbon')].source").value("plan"))
        .andExpect(jsonPath("$.data.plans[0].code").value("starter"));

    override(B, "risk", "true");
    try {
      mvc.perform(get("/v1/auth/me").header("Authorization", adminB))
          .andExpect(jsonPath("$.data.modules", hasItem("risk")))
          .andExpect(jsonPath("$.data.permissions", hasItem("risk:read")));
      mvc.perform(get("/v1/subscription").header("Authorization", adminB))
          .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'risk')].source").value("add_on"))
          .andExpect(jsonPath("$.data.overrides[0].moduleCode").value("risk"))
          .andExpect(jsonPath("$.data.overrides[0].reason").value("WP-19 test"));
      mvc.perform(get("/v1/platform/tenants/" + B + "/subscription").header("Authorization", platform()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.plan").value("starter"))
          .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'risk')].source").value("add_on"));
    } finally {
      override(B, "risk", "null");
    }
  }

  @Test
  void onlyThePlatformChangesEntitlements() throws Exception {
    String body = "{\"enabled\":true,\"reason\":\"self-service upgrade\"}";
    for (String role : new String[] {"TENANT_ADMIN", "ESG_DIRECTOR", "AUDITOR"}) {
      mvc.perform(put("/v1/platform/tenants/" + A + "/modules/investor").header("Authorization", bearerForRole(role))
              .contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get("/v1/subscription").header("Authorization", bearerForRole("TENANT_ADMIN")))
        .andExpect(status().isOk());
    mvc.perform(get("/v1/subscription").header("Authorization", bearerForRole("ESG_ANALYST")))
        .andExpect(status().isForbidden());
  }

  @Test
  void overrideValidation() throws Exception {
    mvc.perform(put("/v1/platform/tenants/" + B + "/modules/risk").header("Authorization", platform())
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.reason").exists());
    mvc.perform(put("/v1/platform/tenants/" + B + "/modules/platform").header("Authorization", platform())
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false,\"reason\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.moduleCode").exists());
    mvc.perform(put("/v1/platform/tenants/" + B + "/modules/teleportation").header("Authorization", platform())
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\"x\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(put("/v1/platform/tenants/99999999-9999-4999-8999-999999999999/modules/risk")
            .header("Authorization", platform())
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\"x\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void theApplicationRoleCannotGrantItselfModules() throws Exception {
    try (Connection app = PostgresTestDatabase.appConnection(); Statement st = app.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + B + "', false)");
      assertThatThrownBy(() -> st.execute("insert into auth.tenant_module_overrides (module_code, enabled, reason) "
          + "values ('investor', true, 'sneaky')")).as("overrides are written only by the definer function");
      assertThatThrownBy(() -> st.execute("update auth.tenants set subscription_tier = 'enterprise'"))
          .as("plan changes go through auth.update_tenant()");
      // Without tenants:update, the definer function refuses too (no user context = no permission).
      assertThatThrownBy(() -> st.execute("select auth.set_module_override('" + B + "', 'investor', true, 'x')"))
          .hasMessageContaining("permission denied");
    }
  }

  @Test
  void tenantsCannotSeeEachOthersOverrides() throws Exception {
    override(A, "investor", "false");
    try (Connection app = PostgresTestDatabase.appConnection(); Statement st = app.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + B + "', false)");
      try (ResultSet rs = st.executeQuery("select count(*) from auth.tenant_module_overrides")) {
        rs.next();
        assertThat(rs.getLong(1)).isZero();
      }
      try (ResultSet rs = st.executeQuery("select count(*) from auth.tenant_modules('" + A + "')")) {
        rs.next();
        assertThat(rs.getLong(1)).as("another tenant's entitlements are invisible").isZero();
      }
    } finally {
      override(A, "investor", "null");
    }
  }

  @Test
  void overrideChangesAreAudited() throws Exception {
    override(B, "analytics", "true");
    override(B, "analytics", "null");
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + B + "', false)");
      try (ResultSet rs = st.executeQuery("select count(*) from audit.audit_logs "
          + "where resource = 'auth.tenant_module_overrides' and action in ('insert', 'delete')")) {
        rs.next();
        assertThat(rs.getLong(1)).isGreaterThanOrEqualTo(2);
      }
    }
  }
}
