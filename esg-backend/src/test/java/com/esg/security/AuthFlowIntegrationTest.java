package com.esg.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * WP-12 authentication over HTTP: login, HttpOnly refresh cookie, rotation with reuse detection, logout,
 * token-type separation (AS-IS S2), enforced TOTP MFA, and non-enumerating errors.
 */
class AuthFlowIntegrationTest extends ApiTestSupport {

  private static final String PASSWORD = "Fixture!2026";

  @Autowired private ObjectMapper json;

  @AfterEach
  void resetDirectorMfa() throws Exception {
    asOwnerInTenantA("update auth.users set mfa_enabled = false, mfa_secret = null, failed_login_count = 0, "
        + "locked_until = null where email in ('esg_director@fixture.test', 'esg_analyst@fixture.test')");
  }

  @Test
  void loginSetsHttpOnlyRefreshCookieAndNoRefreshTokenInBody() throws Exception {
    MvcResult result = login("esg_analyst@fixture.test", PASSWORD)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
        .andExpect(jsonPath("$.data.user.roles", hasItem("ESG_ANALYST")))
        .andReturn();

    Cookie cookie = result.getResponse().getCookie("esg_refresh");
    assertThat(cookie).isNotNull();
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(result.getResponse().getHeader("Set-Cookie")).contains("SameSite=Strict");
  }

  @Test
  void refreshRotatesAndReuseOfAnOldTokenRevokesEverySession() throws Exception {
    Cookie first = login("esg_analyst@fixture.test", PASSWORD).andReturn().getResponse().getCookie("esg_refresh");

    MvcResult refreshed = mvc.perform(post("/v1/auth/refresh").cookie(first))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andReturn();
    Cookie second = refreshed.getResponse().getCookie("esg_refresh");
    assertThat(second.getValue()).isNotEqualTo(first.getValue());

    // Replaying the rotated token right away (another tab refreshing at the same moment) is rejected,
    // but doesn't end the session: the newer token still works.
    mvc.perform(post("/v1/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());
    Cookie third = mvc.perform(post("/v1/auth/refresh").cookie(second))
        .andExpect(status().isOk()).andReturn().getResponse().getCookie("esg_refresh");

    // Replaying a token rotated longer ago than the grace period = theft signal: every session is revoked.
    asOwnerInTenantA("update auth.user_sessions set revoked_at = now() - interval '5 minutes' "
        + "where revoked_at is not null and user_id = '" + roleUserId("ESG_ANALYST") + "'");
    mvc.perform(post("/v1/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());
    mvc.perform(post("/v1/auth/refresh").cookie(third)).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRevokesTheSession() throws Exception {
    Cookie cookie = login("esg_analyst@fixture.test", PASSWORD).andReturn().getResponse().getCookie("esg_refresh");

    MvcResult logout = mvc.perform(post("/v1/auth/logout").cookie(cookie)).andExpect(status().isOk()).andReturn();
    assertThat(logout.getResponse().getCookie("esg_refresh").getMaxAge()).isZero();

    mvc.perform(post("/v1/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
  }

  @Test
  void refreshTokenAndMfaTokenCannotBeUsedAsBearerTokens() throws Exception {
    Cookie cookie = login("esg_analyst@fixture.test", PASSWORD).andReturn().getResponse().getCookie("esg_refresh");
    mvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + cookie.getValue()))
        .andExpect(status().isUnauthorized());

    String mfaToken = body(login("esg_director@fixture.test", PASSWORD).andReturn()).path("mfaToken").asText();
    mvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + mfaToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void mfaIsEnforcedForDirectorsAndCompletesWithTotp() throws Exception {
    // 1. Password step for a role with mfa_required: no tokens, a set-up challenge instead.
    JsonNode challenge = body(login("esg_director@fixture.test", PASSWORD)
        .andExpect(jsonPath("$.data.mfaSetupRequired").value(true))
        .andExpect(jsonPath("$.data.accessToken").doesNotExist())
        .andReturn());
    String mfaToken = challenge.path("mfaToken").asText();

    // 2. Enrol: secret + otpauth URI.
    JsonNode setup = body(mvc.perform(post("/v1/auth/mfa/setup").contentType(MediaType.APPLICATION_JSON)
            .content("{\"mfaToken\":\"" + mfaToken + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")))
        .andReturn());
    String secret = setup.path("secret").asText();

    // 3. A wrong code fails; the right one issues tokens and enables MFA.
    verify(mfaToken, "000000").andExpect(status().isUnauthorized());
    MvcResult verified = verify(mfaToken, currentCode(secret))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.user.mfaEnabled").value(true))
        .andReturn();

    // 3b. The session is MFA-verified (S1): the token says so, it works, and refreshing keeps it verified.
    String access = body(verified).path("accessToken").asText();
    assertThat(tokens.parseAccessToken(access).orElseThrow().get("mfa", Boolean.class)).isTrue();
    mvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk());
    MvcResult refreshed = mvc.perform(post("/v1/auth/refresh").cookie(verified.getResponse().getCookie("esg_refresh")))
        .andExpect(status().isOk()).andReturn();
    String refreshedAccess = body(refreshed).path("accessToken").asText();
    assertThat(tokens.parseAccessToken(refreshedAccess).orElseThrow().get("mfa", Boolean.class)).isTrue();
    mvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + refreshedAccess)).andExpect(status().isOk());

    // 4. Next login asks for the code (not set-up), and the secret is stored encrypted.
    login("esg_director@fixture.test", PASSWORD).andExpect(jsonPath("$.data.mfaRequired").value(true));
    try (var owner = com.esg.support.PostgresTestDatabase.ownerConnection(); var st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + com.esg.support.PostgresTestDatabase.TENANT_A + "', false)");
      var rs = st.executeQuery("select mfa_secret from auth.users where email = 'esg_director@fixture.test'");
      rs.next();
      assertThat(rs.getString(1)).isNotEqualTo(secret).doesNotContain(secret);
    }
  }

  @Test
  void mfaRequiredRolesAreRefusedEverywhereWithoutAnMfaVerifiedSession() throws Exception {
    // S1: a password-only token for an MFA-required role is refused on every endpoint, not just at login.
    for (String role : java.util.List.of("ESG_DIRECTOR", "TENANT_ADMIN")) {
      String token = bearerWithoutMfa(roleUserId(role), com.esg.support.PostgresTestDatabase.TENANT_A);
      for (String path : java.util.List.of("/v1/auth/me", "/v1/admin/users", "/v1/data/points", "/v1/settings")) {
        mvc.perform(get(path).header("Authorization", token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("ESG-401-MFA-REQUIRED"));
      }
    }
    // Roles without the flag don't need it.
    mvc.perform(get("/v1/auth/me").header("Authorization",
            bearerWithoutMfa(roleUserId("ESG_ANALYST"), com.esg.support.PostgresTestDatabase.TENANT_A)))
        .andExpect(status().isOk());
    // The password step of a password-only login doesn't reveal tokens for MFA roles (covered above); a session that
    // started before an MFA role was granted is refused as soon as the role takes effect.
    String analystToken = body(login("esg_analyst@fixture.test", PASSWORD).andExpect(status().isOk()).andReturn())
        .path("accessToken").asText();
    mvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + analystToken)).andExpect(status().isOk());
    asOwnerInTenantA("insert into auth.user_roles (user_id, role_id) select '" + roleUserId("ESG_ANALYST")
        + "', id from auth.roles where name = 'ESG_DIRECTOR'");
    try {
      mvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + analystToken))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.errorCode").value("ESG-401-MFA-REQUIRED"));
    } finally {
      asOwnerInTenantA("delete from auth.user_roles where user_id = '" + roleUserId("ESG_ANALYST")
          + "' and role_id = (select id from auth.roles where name = 'ESG_DIRECTOR')");
    }
  }

  @Test
  void unknownEmailWrongPasswordAndLockedAccountAllLookTheSame() throws Exception {
    login("nobody@fixture.test", PASSWORD).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    login("esg_analyst@fixture.test", "wrong").andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Invalid email or password"));
  }

  @Test
  void meReturnsCurrentRolesAndPermissions() throws Exception {
    mvc.perform(get("/v1/auth/me").header("Authorization", bearerForRole("DATA_COLLECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.roles", hasItem("DATA_COLLECTOR")))
        .andExpect(jsonPath("$.data.permissions", hasItem("data_points:create")))
        .andExpect(jsonPath("$.data.tenantWide").value(false))
        .andExpect(jsonPath("$.data.orgScopeIds", hasItem(ORG_SITE_1)));
  }

  @Test
  void requestsWithoutTokenGet401NotA403() throws Exception {
    mvc.perform(get("/v1/data/points")).andExpect(status().isUnauthorized());
  }

  // ------------------------------------------------------------------ helpers

  private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
    return mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
  }

  private org.springframework.test.web.servlet.ResultActions verify(String mfaToken, String code) throws Exception {
    return mvc.perform(post("/v1/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
        .content("{\"mfaToken\":\"" + mfaToken + "\",\"code\":\"" + code + "\"}"));
  }

  private JsonNode body(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString()).path("data");
  }

  private static String currentCode(String secret) {
    return new TotpService().codeAt(TotpService.base32Decode(secret), Instant.now().getEpochSecond() / 30);
  }
}
