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
  void tenantAdminCannotGrantSuperAdmin() throws Exception {
    String id = createUser(unique("escalate")).path("id").asText();
    mvc.perform(put("/v1/admin/users/" + id + "/roles").header("Authorization", admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"assignments\":[{\"roleId\":\"" + roleId("SUPER_ADMIN") + "\"}]}"))
        .andExpect(status().isForbidden());
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
  void rolesEndpointListsAllTwelveBuiltInRolesWithPermissions() throws Exception {
    mvc.perform(get("/v1/admin/roles").header("Authorization", admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(12)))
        .andExpect(jsonPath("$.data[0].name").value("SUPER_ADMIN"))
        .andExpect(jsonPath("$.data[0].mfaRequired").value(true));
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
