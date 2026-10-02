package com.esg.crosscutting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * S2: tenant settings, module entitlements, reporting-period lock/unlock and role changes are in the audit trail with
 * who made them and the before/after values, and the Audit log screen's table list offers them (GET /audit/resources).
 */
class AdminChangeAuditTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;

  private JsonNode audit(String token, String query) throws Exception {
    String body = mvc.perform(get("/v1/audit/logs?size=200" + query).header("Authorization", token))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return json.readTree(body).path("data").path("content");
  }

  @Test
  void settingsChangeIsAuditedWithBeforeAndAfter() throws Exception {
    String admin = bearerForRole("TENANT_ADMIN");
    JsonNode settings = json.readTree(mvc.perform(get("/v1/settings").header("Authorization", admin))
        .andReturn().getResponse().getContentAsString()).path("data");
    String before = settings.path("fiscalYearStartMonth").asText();
    int after = "4".equals(before) ? 7 : 4;
    String body = "{\"baseCurrency\":\"" + settings.path("baseCurrency").asText() + "\",\"fiscalYearStartMonth\":" + after
        + ",\"reportingTimezone\":\"" + settings.path("reportingTimezone").asText() + "\",\"defaultFrameworkCodes\":[],"
        + "\"version\":" + settings.path("version").asLong() + ",\"provisionalFactorPolicy\":\"allow_flagged\"}";
    mvc.perform(put("/v1/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());

    JsonNode row = audit(admin, "&resource=auth.tenant_settings&action=update").get(0);
    assertThat(row.path("userId").asText()).isEqualTo(roleUserId("TENANT_ADMIN"));
    assertThat(row.path("oldValue").path("fiscal_year_start_month").asText()).isEqualTo(before);
    assertThat(row.path("newValue").path("fiscal_year_start_month").asInt()).isEqualTo(after);
  }

  @Test
  void periodLockAndUnlockAreAudited() throws Exception {
    String admin = bearerForRole("TENANT_ADMIN");
    String name = "Audit " + UUID.randomUUID().toString().substring(0, 6);
    int year = 2040 + (int) (Math.random() * 50);
    String created = mvc.perform(post("/v1/reporting-periods").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + name + "\",\"periodType\":\"annual\",\"startDate\":\"" + year + "-01-01\",\"endDate\":\"" + year + "-12-31\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String id = json.readTree(created).path("data").path("id").asText();
    mvc.perform(post("/v1/reporting-periods/" + id + "/lock").header("Authorization", bearerForRole("ESG_DIRECTOR")))
        .andExpect(status().isOk());
    mvc.perform(post("/v1/reporting-periods/" + id + "/unlock").header("Authorization", bearerForRole("ESG_DIRECTOR")))
        .andExpect(status().isOk());

    JsonNode rows = audit(admin, "&resource=auth.reporting_periods&action=update&resourceId=" + id);
    assertThat(rows).hasSize(2);
    // Newest first: unlock, then lock.
    assertThat(rows.get(1).path("oldValue").path("status").asText()).isEqualTo("open");
    assertThat(rows.get(1).path("newValue").path("status").asText()).isEqualTo("locked");
    assertThat(rows.get(0).path("newValue").path("status").asText()).isEqualTo("open");
    assertThat(rows.get(0).path("userId").asText()).isEqualTo(roleUserId("ESG_DIRECTOR"));
  }

  @Test
  void roleChangesAreAudited() throws Exception {
    String admin = bearerForRole("TENANT_ADMIN");
    String email = "audit.roles." + UUID.randomUUID().toString().substring(0, 8) + "@fixture.test";
    String user = json.readTree(mvc.perform(post("/v1/admin/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"firstName\":\"Role\",\"lastName\":\"Audit\",\"password\":\"Str0ng!Passw0rd\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data").path("id").asText();
    String auditor = roleIdByName("AUDITOR");
    mvc.perform(put("/v1/admin/users/" + user + "/roles").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"assignments\":[{\"roleId\":\"" + auditor + "\"}]}"))
        .andExpect(status().isOk());

    boolean found = false;
    for (JsonNode row : audit(admin, "&resource=auth.user_roles&action=insert")) {
      if (row.path("newValue").path("user_id").asText().equals(user)) {
        assertThat(row.path("newValue").path("role_id").asText()).isEqualTo(auditor);
        assertThat(row.path("userId").asText()).isEqualTo(roleUserId("TENANT_ADMIN"));
        found = true;
      }
    }
    assertThat(found).isTrue();
  }

  @Test
  void moduleEntitlementChangeIsAuditedInTheTenantItChanges() throws Exception {
    mvc.perform(put("/v1/platform/tenants/" + PostgresTestDatabase.TENANT_B + "/modules/training")
            .header("Authorization", bearerForRole("SUPER_ADMIN")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false,\"reason\":\"Audit test " + UUID.randomUUID() + "\"}"))
        .andExpect(status().isOk());
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_B + "', false)");
      ResultSet rs = st.executeQuery("select user_id, new_value ->> 'module_code', new_value ->> 'enabled' from audit.audit_logs "
          + "where resource = 'auth.tenant_module_overrides' order by created_at desc limit 1");
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo(roleUserId("SUPER_ADMIN"));
    }
  }

  @Test
  void auditLogScreenListsTheAuditedTables() throws Exception {
    JsonNode resources = json.readTree(mvc.perform(get("/v1/audit/resources").header("Authorization", bearerForRole("AUDITOR")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    java.util.List<String> values = new java.util.ArrayList<>();
    resources.forEach(r -> values.add(r.path("value").asText()));
    assertThat(values).contains("auth.tenant_settings", "auth.reporting_periods", "auth.user_roles",
        "auth.tenant_module_overrides", "data_hub.data_points");
    resources.forEach(r -> assertThat(r.path("label").asText()).isNotBlank());
  }

  private String roleIdByName(String name) throws Exception {
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      ResultSet rs = st.executeQuery("select id from auth.roles where name = '" + name + "'");
      rs.next();
      return rs.getString(1);
    }
  }
}
