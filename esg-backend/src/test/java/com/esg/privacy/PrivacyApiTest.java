package com.esg.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * WP-52 NDPA 2023 controls through the API (D40): the privacy notice and its acknowledgement, a person's own data,
 * the data subject request register with its due date, erasure by anonymisation (and what is kept), retention.
 */
class PrivacyApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;
  @Autowired private RetentionService retention;

  private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Lagos"));

  private JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  private ResultActions send(MockHttpServletRequestBuilder b, String token, String body) throws Exception {
    return mvc.perform(b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode read(String path, String token) throws Exception {
    return data(mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk()));
  }

  private JsonNode body(String path, String token) throws Exception {
    return json.readTree(mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;"))).andReturn().getResponse().getContentAsString());
  }

  @Test
  void theNoticeIsAcknowledgedPerVersionAndPeopleCanDownloadTheirData() throws Exception {
    String dpo = bearerForRole("COMPLIANCE_OFFICER");
    String analyst = bearerForRole("ESG_ANALYST");
    String notice = "{\"title\":\"How we use your data\",\"body\":\"We process your account and activity data to run the ESG platform.\","
        + "\"dpoContact\":\"dpo@example.org\"}";
    send(post("/v1/privacy/notices"), dpo, notice.replace("\"How we use your data\"", "\"\"")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.title").exists());
    send(post("/v1/privacy/notices"), analyst, notice).andExpect(status().isForbidden());
    JsonNode v1 = data(send(post("/v1/privacy/notices"), dpo, notice).andExpect(status().isCreated()));
    int version = v1.path("version").asInt();

    JsonNode mine = read("/v1/privacy/notice", analyst);
    assertThat(mine.path("acknowledged").asBoolean()).isFalse();
    assertThat(mine.path("notice").path("dpoContact").asText()).isEqualTo("dpo@example.org");
    send(post("/v1/privacy/notice/acknowledge"), analyst, "{\"version\":" + (version + 1) + "}").andExpect(status().isConflict());
    mine = data(send(post("/v1/privacy/notice/acknowledge"), analyst, "{\"version\":" + version + "}").andExpect(status().isOk()));
    assertThat(mine.path("acknowledged").asBoolean()).isTrue();
    // Again is harmless; a new version needs a new acknowledgement.
    send(post("/v1/privacy/notice/acknowledge"), analyst, "{\"version\":" + version + "}").andExpect(status().isOk());
    data(send(post("/v1/privacy/notices"), dpo, notice.replace("run the ESG platform", "run the ESG platform and send reminders"))
        .andExpect(status().isCreated()));
    assertThat(read("/v1/privacy/notice", analyst).path("acknowledged").asBoolean()).isFalse();
    JsonNode list = read("/v1/privacy/notices", dpo);
    assertThat(list.get(1).path("acknowledged").asInt()).isGreaterThanOrEqualTo(1);

    // NDPA s. 34 / 38: one's own data, as a JSON download.
    JsonNode export = body("/v1/privacy/my-data", analyst);
    assertThat(export.path("profile").path("email").asText()).isEqualTo("esg_analyst@fixture.test");
    assertThat(export.path("roles").toString()).contains("ESG_ANALYST");
    assertThat(export.path("privacyAcknowledgements").toString()).contains("How we use your data");
    assertThat(export.has("activity")).isTrue();
    assertThat(export.path("profile").has("password_hash")).isFalse();
    assertThat(export.path("profile").has("mfa_secret")).isFalse();
    mvc.perform(get("/v1/privacy/my-data")).andExpect(status().isUnauthorized());
  }

  @Test
  void requestsAreRegisteredWithADueDateAndAnsweredWithAnExport() throws Exception {
    String dpo = bearerForRole("COMPLIANCE_OFFICER");
    String analystId = roleUserId("ESG_ANALYST");
    String req = "{\"kind\":\"access\",\"subjectUserId\":\"" + analystId + "\",\"subjectName\":\"Esg Analyst\",\"subjectEmail\":\"esg_analyst@fixture.test\","
        + "\"details\":\"Asked by email for a copy of their data\",\"receivedOn\":\"%s\"}";
    send(post("/v1/privacy/requests"), dpo, req.formatted(TODAY.plusDays(1))).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.receivedOn").exists());
    send(post("/v1/privacy/requests"), dpo, req.formatted(TODAY).replace("\"access\"", "\"gossip\"")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.kind").exists());
    send(post("/v1/privacy/requests"), dpo, req.formatted(TODAY).replace("esg_analyst@fixture.test", "not-an-email")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.subjectEmail").exists());
    send(post("/v1/privacy/requests"), bearerForRole("ESG_ANALYST"), req.formatted(TODAY)).andExpect(status().isForbidden());

    // Received 40 days ago: due 30 days after receipt, so overdue.
    JsonNode r = data(send(post("/v1/privacy/requests"), dpo, req.formatted(TODAY.minusDays(40))).andExpect(status().isCreated()));
    assertThat(r.path("reference").asText()).matches("DSR-\\d{4}-\\d{4}");
    assertThat(r.path("dueOn").asText()).isEqualTo(TODAY.minusDays(10).toString());
    assertThat(r.path("overdue").asBoolean()).isTrue();
    String id = r.path("id").asText();
    r = data(send(post("/v1/privacy/requests/" + id + "/actions"), dpo, "{\"action\":\"start\",\"version\":" + r.path("version").asLong() + "}")
        .andExpect(status().isOk()));
    assertThat(r.path("status").asText()).isEqualTo("in_progress");
    assertThat(r.path("handledByName").asText()).isEqualTo("Compliance Officer Fixture");
    JsonNode export = body("/v1/privacy/requests/" + id + "/export", dpo);
    assertThat(export.path("profile").path("id").asText()).isEqualTo(analystId);
    send(post("/v1/privacy/requests/" + id + "/actions"), dpo, "{\"action\":\"complete\",\"version\":" + r.path("version").asLong() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.response").exists());
    r = data(send(post("/v1/privacy/requests/" + id + "/actions"), dpo, "{\"action\":\"complete\",\"response\":\"Export sent by secure link\",\"version\":"
        + r.path("version").asLong() + "}").andExpect(status().isOk()));
    assertThat(r.path("status").asText()).isEqualTo("completed");
    assertThat(r.path("overdue").asBoolean()).isFalse();
    send(post("/v1/privacy/requests/" + id + "/actions"), dpo, "{\"action\":\"refuse\",\"response\":\"x\",\"version\":" + r.path("version").asLong() + "}")
        .andExpect(status().isConflict());
    // An erasure request names the account.
    send(post("/v1/privacy/requests"), dpo, req.formatted(TODAY).replace("\"access\"", "\"erasure\"").replace("\"subjectUserId\":\"" + analystId + "\",", ""))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.subjectUserId").exists());
    mvc.perform(get("/v1/privacy/requests").header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.id == '" + id + "')]").isEmpty());
  }

  @Test
  void erasureAnonymisesTheAccountAndKeepsTheLegalRecord() throws Exception {
    String dpo = bearerForRole("COMPLIANCE_OFFICER");
    String user = UUID.randomUUID().toString();
    String email = "leaver-" + user.substring(0, 8) + "@example.org";
    asOwnerInTenantA(
        "insert into auth.users (id, email, password_hash, first_name, last_name, phone) values ('" + user + "', '" + email + "', 'x', 'Ngozi', 'Leaver', '+2348000000000')",
        "insert into auth.user_roles (user_id, role_id) select '" + user + "', id from auth.roles where name = 'DATA_COLLECTOR' limit 1",
        "insert into auth.user_sessions (user_id, refresh_token_hash, expires_at, ip_address) values ('" + user + "', 'h-" + user + "', now() + interval '1 day', '10.0.0.1')",
        "insert into notifications.notification_queue (user_id, title, message, notification_type) values ('" + user + "', 'Hi', 'Hello Ngozi', 'GENERIC')",
        "insert into notifications.delivery_log (user_id, notification_type, channel, recipient, body, status) values ('" + user + "', 'GENERIC', 'email', '"
            + email + "', 'Hello Ngozi', 'sent')",
        "insert into audit.audit_logs (user_id, action, resource, resource_id) values ('" + user + "', 'update', 'data_hub.data_points', gen_random_uuid())");
    JsonNode r = data(send(post("/v1/privacy/requests"), dpo, "{\"kind\":\"erasure\",\"subjectUserId\":\"" + user + "\",\"subjectName\":\"Ngozi Leaver\","
        + "\"subjectEmail\":\"" + email + "\",\"receivedOn\":\"" + TODAY + "\"}").andExpect(status().isCreated()));
    String id = r.path("id").asText();
    // Completing an erasure without erasing isn't possible.
    send(post("/v1/privacy/requests/" + id + "/actions"), dpo, "{\"action\":\"complete\",\"response\":\"done\",\"version\":" + r.path("version").asLong() + "}")
        .andExpect(status().isConflict());
    mvc.perform(post("/v1/privacy/requests/" + id + "/erase").param("version", "99").header("Authorization", dpo)).andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ESG-409-VERSION"));
    r = data(mvc.perform(post("/v1/privacy/requests/" + id + "/erase").param("version", r.path("version").asText()).header("Authorization", dpo))
        .andExpect(status().isOk()));
    assertThat(r.path("status").asText()).isEqualTo("completed");
    assertThat(r.path("response").asText()).contains("1 role(s), 1 session(s), 1 notification(s) and 1 sent message(s) deleted")
        .contains("Kept as required by law: the audit trail");
    assertThat(r.path("subjectEmail").isMissingNode() || r.path("subjectEmail").isNull()).isTrue();
    try (Connection c = PostgresTestDatabase.ownerConnection(); Statement st = c.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      ResultSet u = st.executeQuery("select first_name, last_name, email, phone, is_active, mfa_secret from auth.users where id = '" + user + "'");
      u.next();
      assertThat(u.getString(1) + " " + u.getString(2)).isEqualTo("Erased user");
      assertThat(u.getString(3)).isEqualTo("erased-" + user + "@erased.invalid");
      assertThat(u.getString(4)).isNull();
      assertThat(u.getBoolean(5)).isFalse();
      ResultSet n = st.executeQuery("select (select count(*) from auth.user_roles where user_id = '" + user + "') + (select count(*) from auth.user_sessions "
          + "where user_id = '" + user + "') + (select count(*) from notifications.notification_queue where user_id = '" + user + "') + (select count(*) "
          + "from notifications.delivery_log where user_id = '" + user + "')");
      n.next();
      assertThat(n.getInt(1)).isZero();
      ResultSet a = st.executeQuery("select count(*) from audit.audit_logs where user_id = '" + user + "'");
      a.next();
      assertThat(a.getInt(1)).as("the audit trail is kept").isGreaterThanOrEqualTo(1);
    }
    mvc.perform(post("/v1/privacy/requests/" + id + "/erase").param("version", r.path("version").asText()).header("Authorization", dpo))
        .andExpect(status().isConflict());
    // Nobody erases their own account.
    JsonNode self = data(send(post("/v1/privacy/requests"), dpo, "{\"kind\":\"erasure\",\"subjectUserId\":\"" + roleUserId("COMPLIANCE_OFFICER")
        + "\",\"subjectName\":\"Me\",\"receivedOn\":\"" + TODAY + "\"}"));
    mvc.perform(post("/v1/privacy/requests/" + self.path("id").asText() + "/erase").param("version", self.path("version").asText())
        .header("Authorization", dpo)).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-SOD"));
  }

  @Test
  void retentionRemovesPersonalDataPastEachTenantsSettings() throws Exception {
    String dpo = bearerForRole("COMPLIANCE_OFFICER");
    JsonNode s = read("/v1/privacy/retention", dpo);
    send(put("/v1/privacy/retention"), dpo, "{\"notificationsDays\":10,\"deliveryLogDays\":90,\"sessionsDays\":30,\"accessLogIpDays\":180,\"version\":"
        + s.path("version").asLong() + "}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.notificationsDays").exists());
    s = data(send(put("/v1/privacy/retention"), dpo, "{\"notificationsDays\":30,\"deliveryLogDays\":30,\"sessionsDays\":7,\"accessLogIpDays\":30,\"version\":"
        + s.path("version").asLong() + "}").andExpect(status().isOk()));
    send(put("/v1/privacy/retention"), dpo, "{\"notificationsDays\":30,\"deliveryLogDays\":30,\"sessionsDays\":7,\"accessLogIpDays\":30,\"version\":99}")
        .andExpect(status().isConflict());
    send(put("/v1/privacy/retention"), bearerForRole("ESG_MANAGER"), "{\"notificationsDays\":30,\"deliveryLogDays\":30,\"sessionsDays\":7,"
        + "\"accessLogIpDays\":30,\"version\":" + s.path("version").asLong() + "}").andExpect(status().isForbidden());

    String user = USER_A;
    String tag = UUID.randomUUID().toString().substring(0, 8);
    asOwnerInTenantA(
        "insert into notifications.notification_queue (user_id, title, message, notification_type, is_read, created_at) values "
            + "('" + user + "', 'old-" + tag + "', 'x', 'GENERIC', true, now() - interval '60 days'), "
            + "('" + user + "', 'unread-" + tag + "', 'x', 'GENERIC', false, now() - interval '60 days'), "
            + "('" + user + "', 'new-" + tag + "', 'x', 'GENERIC', true, now())",
        "insert into notifications.delivery_log (user_id, notification_type, channel, recipient, body, status, created_at) values "
            + "('" + user + "', 'GENERIC', 'email', 'old-" + tag + "@example.org', 'x', 'sent', now() - interval '60 days')",
        "insert into auth.user_sessions (user_id, refresh_token_hash, expires_at, revoked_at, ip_address) values "
            + "('" + user + "', 'ended-" + tag + "', now() - interval '20 days', now() - interval '20 days', '10.1.1.1')",
        "insert into files.file_access_log (file_id, user_id, action, ip_address, created_at) values "
            + "('a0000000-0000-4000-8000-0000000004f1', '" + user + "', 'download', '10.2.2.2', now() - interval '60 days')");
    retention.runAll(Clock.systemUTC());
    try (Connection c = PostgresTestDatabase.ownerConnection(); Statement st = c.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      ResultSet r = st.executeQuery("select (select count(*) from notifications.notification_queue where title = 'old-" + tag + "'), "
          + "(select count(*) from notifications.notification_queue where title in ('unread-" + tag + "', 'new-" + tag + "')), "
          + "(select count(*) from notifications.delivery_log where recipient = 'old-" + tag + "@example.org'), "
          + "(select count(*) from auth.user_sessions where refresh_token_hash = 'ended-" + tag + "'), "
          + "(select count(*) from files.file_access_log where ip_address = '10.2.2.2')");
      r.next();
      assertThat(r.getInt(1)).as("old read notification removed").isZero();
      assertThat(r.getInt(2)).as("unread and recent ones kept").isEqualTo(2);
      assertThat(r.getInt(3)).as("old message log removed").isZero();
      assertThat(r.getInt(4)).as("ended session removed").isZero();
      assertThat(r.getInt(5)).as("old access-log IP cleared").isZero();
    }
  }
}
