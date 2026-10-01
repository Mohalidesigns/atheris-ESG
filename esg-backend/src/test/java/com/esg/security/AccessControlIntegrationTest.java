package com.esg.security;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** WP-13 behaviours beyond the role matrix (TRD §5.2, §5.3.2, §11.2.1). */
class AccessControlIntegrationTest extends ApiTestSupport {

  private static String dataPoint(String orgId) {
    return "{\"orgId\":\"" + orgId + "\",\"category\":\"E\",\"subCategory\":\"Energy\",\"metricKey\":\"diesel_litres\","
        + "\"numericValue\":5,\"unit\":\"L\",\"reportingPeriodStart\":\"2026-01-01\",\"reportingPeriodEnd\":\"2026-03-31\"}";
  }

  @Test
  void orgScopedRoleSeesAndWritesOnlyItsOrganisationSubtree() throws Exception {
    String collector = bearerForRole("DATA_COLLECTOR"); // scoped to Site 1

    mvc.perform(post("/v1/data/points").header("Authorization", collector)
            .contentType(MediaType.APPLICATION_JSON).content(dataPoint(ORG_SITE_1)))
        .andExpect(status().isCreated());
    mvc.perform(post("/v1/data/points").header("Authorization", collector)
            .contentType(MediaType.APPLICATION_JSON).content(dataPoint(ORG_SITE_2)))
        .andExpect(status().isForbidden());

    mvc.perform(get("/v1/data/points?size=100").header("Authorization", collector))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[*].orgId", everyItem(is(ORG_SITE_1))));
    mvc.perform(get("/v1/data/points/" + HQ_DATA_POINT).header("Authorization", collector))
        .andExpect(status().isNotFound());

    // A tenant-wide role sees the HQ point.
    mvc.perform(get("/v1/data/points?size=100").header("Authorization", bearerForRole("AUDITOR")))
        .andExpect(jsonPath("$.data.content[*].orgId", hasItem(ORG_HQ)));
  }

  @Test
  void organisationsListIsScopedToo() throws Exception {
    mvc.perform(get("/v1/admin/organisations").header("Authorization", bearerForRole("ESG_ANALYST")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].id", everyItem(is(ORG_SITE_1))));
  }

  @Test
  void expiredRoleLosesAccessImmediatelyWithTheSameToken() throws Exception {
    String auditor = bearerForRole("AUDITOR");
    mvc.perform(get("/v1/data/points").header("Authorization", auditor)).andExpect(status().isOk());

    String userId = roleUserId("AUDITOR");
    asOwnerInTenantA("update auth.user_roles set expires_at = now() - interval '1 minute' where user_id = '" + userId + "'");
    try {
      mvc.perform(get("/v1/data/points").header("Authorization", auditor)).andExpect(status().isForbidden());
    } finally {
      asOwnerInTenantA("update auth.user_roles set expires_at = null where user_id = '" + userId + "'");
    }
  }

  @Test
  void deactivatedUserIsRejectedImmediately() throws Exception {
    String consultant = bearerForRole("CONSULTANT");
    String userId = roleUserId("CONSULTANT");
    asOwnerInTenantA("update auth.users set is_active = false where id = '" + userId + "'");
    try {
      mvc.perform(get("/v1/auth/me").header("Authorization", consultant)).andExpect(status().isUnauthorized());
    } finally {
      asOwnerInTenantA("update auth.users set is_active = true where id = '" + userId + "'");
    }
  }

  @Test
  void contactDetailsAreMaskedWithoutUsersReadPii() throws Exception {
    // TRAINING_ADMIN has users:read but not users:read_pii.
    mvc.perform(get("/v1/admin/users/" + USER_A).header("Authorization", bearerForRole("TRAINING_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("user.a@fixture.test"))
        .andExpect(jsonPath("$.data.phone").value(nullValue()));
    mvc.perform(get("/v1/admin/users/" + USER_A).header("Authorization", bearerForRole("TENANT_ADMIN")))
        .andExpect(jsonPath("$.data.phone").value(notNullValue()));
  }

  @Test
  void creatorCannotApproveTheirOwnDataPointAndStatusIsValidated() throws Exception {
    // USER_A created the HQ data point and holds ESG_DIRECTOR (data_points:approve).
    String creator = bearer(USER_A, PostgresTestDatabase.TENANT_A);
    mvc.perform(put("/v1/data/points/" + HQ_DATA_POINT + "/validate").header("Authorization", creator)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"validated\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(put("/v1/data/points/" + HQ_DATA_POINT + "/validate").header("Authorization", bearerForRole("ESG_DIRECTOR"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"approved-by-magic\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.data.status").exists());
  }

  @Test
  void otherTenantCannotSeeTenantAUsers() throws Exception {
    mvc.perform(get("/v1/admin/users/" + USER_A).header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B)))
        .andExpect(status().isNotFound());
  }
}
