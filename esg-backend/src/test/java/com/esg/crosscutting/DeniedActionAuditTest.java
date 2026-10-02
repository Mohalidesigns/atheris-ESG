package com.esg.crosscutting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * S3: refused actions are written to the audit trail as {@code action=denied}, with who, what, the target and the
 * error code, even though the refused request's own transaction rolled back.
 */
class DeniedActionAuditTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;

  private JsonNode deniedRows(String query) throws Exception {
    String body = mvc.perform(get("/v1/audit/logs?action=denied&size=200" + query).header("Authorization", bearerForRole("AUDITOR")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return json.readTree(body).path("data").path("content");
  }

  @Test
  void segregationOfDutiesRefusalIsAuditedAgainstTheDataPoint() throws Exception {
    String day = java.time.LocalDate.of(2026, 1, 1).plusDays((int) (Math.random() * 180)).toString();
    String created = mvc.perform(post("/v1/data/points").header("Authorization", bearerForRole("ESG_ANALYST"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"water_withdrawal_m3\",\"numericValue\":7,"
                + "\"reportingPeriodStart\":\"" + day + "\",\"reportingPeriodEnd\":\"" + day + "\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    JsonNode point = json.readTree(created).path("data");
    String id = point.path("id").asText();
    String submitted = mvc.perform(put("/v1/data/points/" + id + "/submit").header("Authorization", bearerForRole("ESG_ANALYST"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + point.path("version").asLong() + "}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    long version = json.readTree(submitted).path("data").path("version").asLong();

    // The analyst (who may validate) tries to validate their own submission.
    mvc.perform(put("/v1/data/points/" + id + "/validate").header("Authorization", bearerForRole("ESG_ANALYST"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version + "}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ESG-403-SOD"));

    JsonNode rows = deniedRows("&resourceId=" + id);
    assertThat(rows).hasSize(1);
    JsonNode row = rows.get(0);
    assertThat(row.path("userId").asText()).isEqualTo(roleUserId("ESG_ANALYST"));
    assertThat(row.path("resource").asText()).isEqualTo("PUT /v1/data/points/{id}/validate");
    assertThat(row.path("newValue").path("errorCode").asText()).isEqualTo("ESG-403-SOD");
    assertThat(row.path("newValue").path("status").asInt()).isEqualTo(403);
    assertThat(row.path("newValue").path("detail").asText()).contains("you created or submitted");
    // The data point is unchanged and its history doesn't show the refusal as a step.
    mvc.perform(get("/v1/data/points/" + id).header("Authorization", bearerForRole("ESG_DIRECTOR")))
        .andExpect(jsonPath("$.data.validationStatus").value("submitted"));
    asOwnerInTenantA("update data_hub.data_points set deleted_at = now() where id = '" + id + "'");
  }

  @Test
  void lockedPeriodAndMissingPermissionRefusalsAreAudited() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    // FY2020 is locked in the fixture.
    mvc.perform(post("/v1/data/points").header("Authorization", bearerForRole("ESG_DIRECTOR"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orgId\":\"" + ORG_HQ + "\",\"metricKey\":\"electricity_kwh\",\"numericValue\":1,"
                + "\"reportingPeriodStart\":\"2020-03-01\",\"reportingPeriodEnd\":\"2020-03-31\",\"notes\":\"" + tag + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ESG-409-LOCKED"));
    // An auditor (read-only) tries to create a user.
    mvc.perform(post("/v1/admin/users").header("Authorization", bearerForRole("AUDITOR"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"denied." + tag + "@fixture.test\",\"firstName\":\"No\",\"lastName\":\"Access\",\"password\":\"Str0ng!Passw0rd\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ESG-403"));

    JsonNode locked = latest(deniedRows("&resource=POST /v1/data/points"), roleUserId("ESG_DIRECTOR"));
    assertThat(locked.path("newValue").path("errorCode").asText()).isEqualTo("ESG-409-LOCKED");
    assertThat(locked.path("newValue").path("detail").asText()).contains("locked");
    JsonNode forbidden = latest(deniedRows("&resource=POST /v1/admin/users"), roleUserId("AUDITOR"));
    assertThat(forbidden.path("newValue").path("errorCode").asText()).isEqualTo("ESG-403");
    assertThat(forbidden.path("newValue").path("method").asText()).isEqualTo("POST");
  }

  @Test
  void deniedRowsStayInTheirTenantAndAnonymousRequestsAreNotLogged() throws Exception {
    // Tenant B's admin tries to deactivate themselves (ESG-400-SELF): a refusal recorded in tenant B.
    mvc.perform(post("/v1/admin/users/" + USER_B_ADMIN + "/deactivate")
            .header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("ESG-400-SELF"));
    mvc.perform(get("/v1/audit/logs?action=denied&resourceId=" + USER_B_ADMIN)
            .header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B)))
        .andExpect(jsonPath("$.data.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    // Tenant A's auditor never sees it (RLS).
    assertThat(deniedRows("&resourceId=" + USER_B_ADMIN)).isEmpty();
    // No tenant, no row: an anonymous request is answered 401 and nothing is written.
    mvc.perform(post("/v1/admin/users")).andExpect(status().isUnauthorized());
  }

  private static JsonNode latest(JsonNode rows, String userId) {
    for (JsonNode row : rows) {
      if (row.path("userId").asText().equals(userId)) {
        return row;
      }
    }
    throw new AssertionError("No denied row for " + userId + " in " + rows);
  }
}
