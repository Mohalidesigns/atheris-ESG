package com.esg.crosscutting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Database-level audit trail, immutability, viewer API and data lineage (WP-15, TRD §6.6.1–6.6.2). */
class AuditTrailTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;

  private JsonNode data(String body) throws Exception {
    return json.readTree(body).path("data");
  }

  @Test
  void writesAreAuditedWithActorAndClientWithoutSecretsAndUpdatesStoreOnlyChanges() throws Exception {
    String admin = bearerForRole("TENANT_ADMIN");
    String email = "audited." + UUID.randomUUID().toString().substring(0, 6) + "@fixture.test";
    JsonNode user = data(mvc.perform(post("/v1/admin/users").header("Authorization", admin).header("User-Agent", "audit-test")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"password\":\"Str0ng!Passw0rd\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    String id = user.path("id").asText();

    JsonNode created = data(mvc.perform(get("/v1/audit/logs?resource=auth.users&action=insert&resourceId=" + id)
            .header("Authorization", admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
        .path("content").get(0);
    assertThat(created.path("userId").asText()).isEqualTo(roleUserId("TENANT_ADMIN"));
    assertThat(created.path("ipAddress").asText()).isEqualTo("127.0.0.1");
    assertThat(created.path("userAgent").asText()).isEqualTo("audit-test");
    assertThat(created.path("newValue").path("email").asText()).isEqualTo(email);
    assertThat(created.path("newValue").has("password_hash")).as("password hash must never be logged").isFalse();

    mvc.perform(put("/v1/admin/users/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"firstName\":\"Ada\",\"lastName\":\"Byron\",\"version\":" + user.path("version").asLong() + "}"))
        .andExpect(status().isOk());
    JsonNode updated = data(mvc.perform(get("/v1/audit/logs?resource=auth.users&action=update&resourceId=" + id)
            .header("Authorization", admin)).andReturn().getResponse().getContentAsString()).path("content").get(0);
    assertThat(updated.path("oldValue").path("last_name").asText()).isEqualTo("Lovelace");
    assertThat(updated.path("newValue").path("last_name").asText()).isEqualTo("Byron");
    assertThat(updated.path("newValue").has("first_name")).as("unchanged columns are not stored").isFalse();
  }

  /** Tables V014 deliberately leaves out of the change log (high-volume technical tables, the log itself). */
  private static final List<String> NOT_CHANGE_LOGGED = List.of("auth.login_identities", "auth.user_sessions",
      "auth.role_permissions", "data_hub.offline_sync_queue", "notifications.notification_queue",
      "notifications.delivery_log", "files.file_access_log", "files.file_versions", "audit.audit_logs",
      "audit.data_lineage", "integrations.api_key_identities", "integrations.webhook_deliveries");

  /** Guards new migrations (WP-18): a tenant table without the change-log trigger would be silently unaudited. */
  @Test
  void everyTenantTableHasTheChangeLogAndAuditColumnTriggers() throws SQLException {
    String sql = "select c.oid::regclass::text, "
        + "exists (select 1 from pg_trigger t where t.tgrelid = c.oid and t.tgname = 'audit_log'), "
        + "exists (select 1 from pg_trigger t where t.tgrelid = c.oid and t.tgname = 'touch_audit_columns'), "
        + "exists (select 1 from pg_attribute a where a.attrelid = c.oid and a.attname = 'updated_at') "
        + "from pg_class c join pg_namespace n on n.oid = c.relnamespace "
        + "join pg_attribute a on a.attrelid = c.oid and a.attname = 'tenant_id' and not a.attisdropped "
        + "where c.relkind in ('r','p') and not c.relispartition and n.nspname not in ('pg_catalog', 'information_schema')";
    List<String> missing = new ArrayList<>();
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      while (rs.next()) {
        String table = rs.getString(1);
        if (!NOT_CHANGE_LOGGED.contains(table) && !rs.getBoolean(2)) {
          missing.add(table + " (audit_log trigger)");
        }
        if (rs.getBoolean(4) && !rs.getBoolean(3)) {
          missing.add(table + " (touch_audit_columns trigger)");
        }
      }
    }
    assertThat(missing).as("call auth.apply_audit_log_trigger / auth.apply_audit_trigger in the migration").isEmpty();
  }

  @Test
  void auditLogIsImmutableEvenForTheSchemaOwner() throws SQLException {
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      assertThatThrownBy(() -> st.executeUpdate("update audit.audit_logs set action = 'forged'"))
          .isInstanceOf(SQLException.class).hasMessageContaining("immutable");
      assertThatThrownBy(() -> st.executeUpdate("delete from audit.audit_logs"))
          .isInstanceOf(SQLException.class).hasMessageContaining("immutable");
    }
  }

  @Test
  void auditViewerRequiresAuditPermission() throws Exception {
    mvc.perform(get("/v1/audit/logs").header("Authorization", bearerForRole("AUDITOR"))).andExpect(status().isOk());
    mvc.perform(get("/v1/audit/logs").header("Authorization", bearerForRole("DATA_COLLECTOR"))).andExpect(status().isForbidden());
  }

  @Test
  void auditViewerFiltersAndValidatesParameters() throws Exception {
    String admin = bearerForRole("TENANT_ADMIN");
    JsonNode org = data(mvc.perform(post("/v1/organisations").header("Authorization", admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Audited site\",\"orgType\":\"site\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    String id = org.path("id").asText();
    mvc.perform(put("/v1/organisations/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Audited site 2\",\"orgType\":\"site\",\"version\":" + org.path("version").asLong() + "}"))
        .andExpect(status().isOk());

    String base = "/v1/audit/logs?resource=auth.organisations&resourceId=" + id;
    mvc.perform(get(base).header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(jsonPath("$.data.content[*].resourceId", everyItem(is(id))));
    mvc.perform(get(base + "&action=update").header("Authorization", admin))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].action").value("update"));
    mvc.perform(get(base + "&from=2999-01-01T00:00:00Z").header("Authorization", admin))
        .andExpect(jsonPath("$.data.content.length()").value(0));
    mvc.perform(get(base + "&to=2000-01-01T00:00:00Z").header("Authorization", admin))
        .andExpect(jsonPath("$.data.content.length()").value(0));
    mvc.perform(get("/v1/audit/logs?resourceId=not-a-uuid").header("Authorization", admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.resourceId").exists());
    mvc.perform(get("/v1/audit/logs?from=yesterday").header("Authorization", admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.from").exists());
  }

  @Test
  void emissionCalculatedFromAFactorRecordsLineage() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    JsonNode factors = data(mvc.perform(get("/v1/carbon/factors?region=NG").header("Authorization", director))
        .andReturn().getResponse().getContentAsString());
    String dieselFactor = null;
    for (JsonNode f : factors) {
      if (f.path("category").asText().equals("diesel_generator")) {
        dieselFactor = f.path("id").asText();
      }
    }
    JsonNode emission = data(mvc.perform(post("/v1/carbon/emissions").header("Authorization", director)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orgId\":\"" + ORG_HQ + "\",\"scope\":\"scope_1\",\"sourceActivity\":\"diesel_generator\",\"activityData\":1000,"
                + "\"activityUnit\":\"litre\",\"emissionFactorId\":\"" + dieselFactor + "\",\"reportingPeriodStart\":\"2026-04-01\","
                + "\"reportingPeriodEnd\":\"2026-04-30\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.co2eTonnes").value(2.68))
        .andReturn().getResponse().getContentAsString());

    mvc.perform(get("/v1/audit/lineage?type=emission_record&id=" + emission.path("id").asText()).header("Authorization", director))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.upstream[0].sourceType").value("emission_factor"))
        .andExpect(jsonPath("$.data.upstream[0].sourceId").value(dieselFactor))
        .andExpect(jsonPath("$.data.upstream[0].relation").value("calculated_from"));
  }
}
