package com.esg.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Admin Console user and role-assignment API (WP-13): field round-trip (master plan §7.2), validation matrix,
 * optimistic locking, and the role-assignment rules of TRD §5.5.
 */
class AdminUserApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;

  private String admin() {
    return bearerForRole("TENANT_ADMIN");
  }

  private JsonNode createUser(String email) throws Exception {
    String body = "{\"email\":\"" + email + "\",\"firstName\":\"Ngozi\",\"lastName\":\"Eze\",\"phone\":\"+2348031234567\","
        + "\"password\":\"Str0ng!Passw0rd\",\"preferredLanguage\":\"fr\",\"timezone\":\"Africa/Nairobi\"}";
    String response = mvc.perform(post("/v1/admin/users").header("Authorization", admin())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return json.readTree(response).path("data");
  }

  private static String unique(String prefix) {
    return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@fixture.test";
  }

  @Test
  void createAllFieldsRoundTrips() throws Exception {
    String email = unique("create");
    String id = createUser(email).path("id").asText();

    mvc.perform(get("/v1/admin/users/" + id).header("Authorization", admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value(email))
        .andExpect(jsonPath("$.data.firstName").value("Ngozi"))
        .andExpect(jsonPath("$.data.lastName").value("Eze"))
        .andExpect(jsonPath("$.data.phone").value("+2348031234567"))
        .andExpect(jsonPath("$.data.preferredLanguage").value("fr"))
        .andExpect(jsonPath("$.data.timezone").value("Africa/Nairobi"))
        .andExpect(jsonPath("$.data.active").value(true))
        .andExpect(jsonPath("$.data.mfaEnabled").value(false))
        .andExpect(jsonPath("$.data.password").doesNotExist());
  }

  @Test
  void updateEachFieldChangesOnlyThatField() throws Exception {
    JsonNode created = createUser(unique("update"));
    String id = created.path("id").asText();
    long version = created.path("version").asLong();

    String[][] changes = {
      {"firstName", "Chiamaka"}, {"lastName", "Obi"}, {"phone", "+254712345678"},
      {"preferredLanguage", "sw"}, {"timezone", "Africa/Accra"}};
    String first = "Ngozi", last = "Eze", phone = "+2348031234567", lang = "fr", tz = "Africa/Nairobi";
    for (String[] change : changes) {
      switch (change[0]) {
        case "firstName" -> first = change[1];
        case "lastName" -> last = change[1];
        case "phone" -> phone = change[1];
        case "preferredLanguage" -> lang = change[1];
        default -> tz = change[1];
      }
      String body = String.format("{\"firstName\":\"%s\",\"lastName\":\"%s\",\"phone\":\"%s\",\"preferredLanguage\":\"%s\","
          + "\"timezone\":\"%s\",\"version\":%d}", first, last, phone, lang, tz, version);
      String response = mvc.perform(put("/v1/admin/users/" + id).header("Authorization", admin())
              .contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.firstName").value(first))
          .andExpect(jsonPath("$.data.lastName").value(last))
          .andExpect(jsonPath("$.data.phone").value(phone))
          .andExpect(jsonPath("$.data.preferredLanguage").value(lang))
          .andExpect(jsonPath("$.data.timezone").value(tz))
          .andReturn().getResponse().getContentAsString();
      version = json.readTree(response).path("data").path("version").asLong();
    }
  }

  @Test
  void staleVersionIsRejectedWith409() throws Exception {
    JsonNode created = createUser(unique("stale"));
    String body = "{\"firstName\":\"A\",\"lastName\":\"B\",\"version\":" + (created.path("version").asLong() + 7) + "}";
    mvc.perform(put("/v1/admin/users/" + created.path("id").asText()).header("Authorization", admin())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
  }

  static Stream<Arguments> invalidCreates() {
    String base = "\"firstName\":\"A\",\"lastName\":\"B\",\"password\":\"Str0ng!Passw0rd\"";
    return Stream.of(
        Arguments.of("email", "{\"email\":\"not-an-email\"," + base + "}"),
        Arguments.of("email", "{\"email\":\"\"," + base + "}"),
        Arguments.of("firstName", "{\"email\":\"v1@fixture.test\",\"firstName\":\"\",\"lastName\":\"B\",\"password\":\"Str0ng!Passw0rd\"}"),
        Arguments.of("lastName", "{\"email\":\"v2@fixture.test\",\"firstName\":\"A\",\"lastName\":\"" + "x".repeat(101) + "\",\"password\":\"Str0ng!Passw0rd\"}"),
        Arguments.of("phone", "{\"email\":\"v3@fixture.test\"," + base + ",\"phone\":\"12ab\"}"),
        Arguments.of("preferredLanguage", "{\"email\":\"v4@fixture.test\"," + base + ",\"preferredLanguage\":\"de\"}"),
        Arguments.of("password", "{\"email\":\"v5@fixture.test\",\"firstName\":\"A\",\"lastName\":\"B\",\"password\":\"weakpassword\"}"),
        Arguments.of("email", "{\"email\":\"user.a@fixture.test\"," + base + "}"));
  }

  @ParameterizedTest(name = "{0}: {1}")
  @MethodSource("invalidCreates")
  void validationErrorsAreReportedPerField(String field, String body) throws Exception {
    mvc.perform(post("/v1/admin/users").header("Authorization", admin())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors." + field).exists());
  }

  @Test
  void roleAssignmentWithScopeAndExpiryRoundTrips() throws Exception {
    String id = createUser(unique("roles")).path("id").asText();
    String analystRole = roleId("ESG_ANALYST");
    Instant expires = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    mvc.perform(put("/v1/admin/users/" + id + "/roles").header("Authorization", admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"assignments\":[{\"roleId\":\"" + analystRole + "\",\"orgScopeId\":\"" + ORG_SITE_2
                + "\",\"expiresAt\":\"" + expires + "\"}]}"))
        .andExpect(status().isOk());

    mvc.perform(get("/v1/admin/users/" + id + "/roles").header("Authorization", admin()))
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].roleName").value("ESG_ANALYST"))
        .andExpect(jsonPath("$.data[0].orgScopeId").value(ORG_SITE_2))
        .andExpect(jsonPath("$.data[0].orgScopeName").value("A Site 2"))
        .andExpect(jsonPath("$.data[0].expiresAt").value(expires.toString()));
  }

  @Test
  void orgScopedRoleRequiresAnOrganisation() throws Exception {
    String id = createUser(unique("scope")).path("id").asText();
    mvc.perform(put("/v1/admin/users/" + id + "/roles").header("Authorization", admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"assignments\":[{\"roleId\":\"" + roleId("DATA_COLLECTOR") + "\"}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['assignments[0].orgScopeId']").exists());
  }

  @Test
  void expiryMustBeInTheFuture() throws Exception {
    String id = createUser(unique("expiry")).path("id").asText();
    mvc.perform(put("/v1/admin/users/" + id + "/roles").header("Authorization", admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"assignments\":[{\"roleId\":\"" + roleId("AUDITOR") + "\",\"expiresAt\":\"2020-01-01T00:00:00Z\"}]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void tenantAdminCannotGrantSuperAdminAndTheRefusalIsAudited() throws Exception {
    // S4: SUPER_ADMIN isn't offered to a tenant admin, but the server still refuses it if sent directly (S3: audited).
    String id = createUser(unique("escalate")).path("id").asText();
    String superAdmin = superAdminRoleId();
    mvc.perform(put("/v1/admin/users/" + id + "/roles").header("Authorization", admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"assignments\":[{\"roleId\":\"" + superAdmin + "\"}]}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ESG-403-ROLE"));
    mvc.perform(get("/v1/audit/logs?action=denied&resourceId=" + id).header("Authorization", admin()))
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].userId").value(roleUserId("TENANT_ADMIN")))
        .andExpect(jsonPath("$.data.content[0].resource").value("PUT /v1/admin/users/{id}/roles"))
        .andExpect(jsonPath("$.data.content[0].newValue.errorCode").value("ESG-403-ROLE"));
  }

  @Test
  void rolePickerOffersOnlyRolesTheCallerMayAssign() throws Exception {
    // S4: a tenant admin never sees platform roles; assignable=true leaves out roles with permissions they lack.
    String all = mvc.perform(get("/v1/admin/roles").header("Authorization", admin()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    java.util.List<String> names = new java.util.ArrayList<>();
    json.readTree(all).path("data").forEach(r -> names.add(r.path("name").asText()));
    org.assertj.core.api.Assertions.assertThat(names).doesNotContain("SUPER_ADMIN").contains("TENANT_ADMIN", "AUDITOR");

    String assignable = mvc.perform(get("/v1/admin/roles?assignable=true").header("Authorization", admin()))
        .andReturn().getResponse().getContentAsString();
    json.readTree(assignable).path("data").forEach(r ->
        org.assertj.core.api.Assertions.assertThat(r.path("assignable").asBoolean()).as(r.path("name").asText()).isTrue());
    // Every listed role can actually be granted (no picker option the server would refuse).
    String id = createUser(unique("pickall")).path("id").asText();
    for (JsonNode role : json.readTree(assignable).path("data")) {
      String scope = "org".equals(role.path("scopeType").asText()) ? ",\"orgScopeId\":\"" + ORG_SITE_1 + "\"" : "";
      mvc.perform(put("/v1/admin/users/" + id + "/roles").header("Authorization", admin())
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"assignments\":[{\"roleId\":\"" + role.path("id").asText() + "\"" + scope + "}]}"))
          .andExpect(status().isOk());
    }
    // A super admin sees and may assign SUPER_ADMIN.
    mvc.perform(get("/v1/admin/roles?assignable=true").header("Authorization", bearerForRole("SUPER_ADMIN")))
        .andExpect(jsonPath("$.data[0].name").value("SUPER_ADMIN"));
  }

  @Test
  void platformUsersAreHiddenFromTenantAdmins() throws Exception {
    // S5: the fixture SUPER_ADMIN belongs to tenant A but isn't part of the tenant's user list, lookups or seats.
    String superAdmin = roleUserId("SUPER_ADMIN");
    String list = mvc.perform(get("/v1/admin/users?size=100").header("Authorization", admin()))
        .andReturn().getResponse().getContentAsString();
    org.assertj.core.api.Assertions.assertThat(list).doesNotContain(superAdmin);
    mvc.perform(get("/v1/admin/users/" + superAdmin).header("Authorization", admin())).andExpect(status().isNotFound());
    mvc.perform(get("/v1/admin/users/" + superAdmin + "/roles").header("Authorization", admin())).andExpect(status().isNotFound());
    mvc.perform(post("/v1/admin/users/" + superAdmin + "/deactivate").header("Authorization", admin()))
        .andExpect(status().isNotFound());
    // The platform user still sees everyone, themselves included.
    org.assertj.core.api.Assertions.assertThat(mvc.perform(get("/v1/admin/users?size=100")
            .header("Authorization", bearerForRole("SUPER_ADMIN"))).andReturn().getResponse().getContentAsString())
        .contains(superAdmin);
  }

  @Test
  void adminCannotRemoveTheirOwnAbilityToAssignRoles() throws Exception {
    mvc.perform(put("/v1/admin/users/" + roleUserId("TENANT_ADMIN") + "/roles").header("Authorization", admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"assignments\":[{\"roleId\":\"" + roleId("AUDITOR") + "\"}]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deactivateRevokesAccess() throws Exception {
    String id = createUser(unique("deactivate")).path("id").asText();
    mvc.perform(post("/v1/admin/users/" + id + "/deactivate").header("Authorization", admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.active").value(false));
    mvc.perform(get("/v1/auth/me").header("Authorization", bearer(id, com.esg.support.PostgresTestDatabase.TENANT_A)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void searchFindsByNameAndEmail() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    String body = "{\"email\":\"finder." + tag + "@fixture.test\",\"firstName\":\"Adeola\",\"lastName\":\"Zz" + tag
        + "\",\"password\":\"Str0ng!Passw0rd\"}";
    mvc.perform(post("/v1/admin/users").header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());

    mvc.perform(get("/v1/admin/users?search=finder." + tag).header("Authorization", admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].email").value("finder." + tag + "@fixture.test"));
    mvc.perform(get("/v1/admin/users?search=ZZ" + tag.toUpperCase()).header("Authorization", admin()))
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].lastName").value("Zz" + tag));
    mvc.perform(get("/v1/admin/users?search=no-such-person-" + tag).header("Authorization", admin()))
        .andExpect(jsonPath("$.data.content", hasSize(0)));
    // Another tenant's admin searching for the same text finds nothing.
    mvc.perform(get("/v1/admin/users?search=finder." + tag)
            .header("Authorization", bearer(USER_B_ADMIN, com.esg.support.PostgresTestDatabase.TENANT_B)))
        .andExpect(jsonPath("$.data.content", hasSize(0)));
  }

  @Test
  void userListFiltersByRoleAndStatusAndSortsByName() throws Exception {
    // Phase 2: role and status filters; sorted by first name.
    String auditors = mvc.perform(get("/v1/admin/users?role=AUDITOR&size=100").header("Authorization", admin()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    for (JsonNode u : json.readTree(auditors).path("data").path("content")) {
      org.assertj.core.api.Assertions.assertThat(u.path("roles").toString()).contains("AUDITOR");
    }
    org.assertj.core.api.Assertions.assertThat(json.readTree(auditors).path("data").path("content").size()).isPositive();
    String id = createUser(unique("inactive")).path("id").asText();
    mvc.perform(post("/v1/admin/users/" + id + "/deactivate").header("Authorization", admin())).andExpect(status().isOk());
    String inactive = mvc.perform(get("/v1/admin/users?status=inactive&size=100").header("Authorization", admin()))
        .andReturn().getResponse().getContentAsString();
    org.assertj.core.api.Assertions.assertThat(inactive).contains(id);
    for (JsonNode u : json.readTree(inactive).path("data").path("content")) {
      org.assertj.core.api.Assertions.assertThat(u.path("active").asBoolean()).isFalse();
    }
    mvc.perform(get("/v1/admin/users?status=sleeping").header("Authorization", admin()))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.status").exists());
    java.util.List<String> names = new java.util.ArrayList<>();
    json.readTree(mvc.perform(get("/v1/admin/users?size=100").header("Authorization", admin())).andReturn().getResponse()
        .getContentAsString()).path("data").path("content").forEach(u -> names.add(u.path("firstName").asText().toLowerCase()));
    org.assertj.core.api.Assertions.assertThat(names).isSorted();
  }

  @Test
  void integrationsReportEachExternalService() throws Exception {
    // Phase 2, item 11: status straight from the adapters (test profile: sandbox scanner, no SMTP, no SMS key).
    mvc.perform(get("/v1/integrations").header("Authorization", admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.key == 'virus_scanner')].status").value(org.hamcrest.Matchers.contains("sandbox")))
        .andExpect(jsonPath("$.data[?(@.key == 'satellite_flaring')].status").value(org.hamcrest.Matchers.contains("sandbox")))
        .andExpect(jsonPath("$.data[?(@.key == 'regulator_feed')].status").value(org.hamcrest.Matchers.contains("not_configured")))
        .andExpect(jsonPath("$.data.length()").value(5));
    mvc.perform(get("/v1/integrations").header("Authorization", bearerForRole("DATA_COLLECTOR"))).andExpect(status().isForbidden());
  }

  @Test
  void rolesEndpointListsTheElevenTenantRolesWithPermissions() throws Exception {
    mvc.perform(get("/v1/admin/roles").header("Authorization", admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(11)))
        .andExpect(jsonPath("$.data[0].name").value("TENANT_ADMIN"))
        .andExpect(jsonPath("$.data[0].mfaRequired").value(true));
    mvc.perform(get("/v1/admin/roles").header("Authorization", bearerForRole("SUPER_ADMIN")))
        .andExpect(jsonPath("$.data", hasSize(12)));
  }

  private String superAdminRoleId() throws Exception {
    try (var owner = com.esg.support.PostgresTestDatabase.ownerConnection(); var st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + com.esg.support.PostgresTestDatabase.TENANT_A + "', false)");
      var rs = st.executeQuery("select id from auth.roles where name = 'SUPER_ADMIN'");
      rs.next();
      return rs.getString(1);
    }
  }

  private String roleId(String name) throws Exception {
    String response = mvc.perform(get("/v1/admin/roles").header("Authorization", admin()))
        .andReturn().getResponse().getContentAsString();
    for (JsonNode role : json.readTree(response).path("data")) {
      if (role.path("name").asText().equals(name)) {
        return role.path("id").asText();
      }
    }
    throw new IllegalStateException("No role " + name);
  }
}
