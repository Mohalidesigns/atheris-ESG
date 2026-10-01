package com.esg.crosscutting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.notifications.EmailSender;
import com.esg.notifications.NotificationDispatcher;
import com.esg.notifications.SendResult;
import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Notification service: templates, preferences, in-app inbox, and the email outbox dispatcher (WP-15, TRD §6.6.3). */
class NotificationServiceTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;
  @Autowired private NotificationDispatcher dispatcher;
  @MockitoBean private EmailSender email;

  private void changeRoles(String userId, String roleName) throws Exception {
    String admin = bearerForRole("TENANT_ADMIN");
    JsonNode roles = json.readTree(mvc.perform(get("/v1/admin/roles").header("Authorization", admin))
        .andReturn().getResponse().getContentAsString()).path("data");
    String roleId = null;
    for (JsonNode r : roles) {
      if (r.path("name").asText().equals(roleName)) {
        roleId = r.path("id").asText();
      }
    }
    mvc.perform(put("/v1/admin/users/" + userId + "/roles").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
        .content("{\"assignments\":[{\"roleId\":\"" + roleId + "\"}]}")).andExpect(status().isOk());
  }

  private String sql(String query) throws Exception {
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      ResultSet rs = st.executeQuery(query);
      return rs.next() ? rs.getString(1) : null;
    }
  }

  @Test
  void roleChangeNotifiesInAppAndQueuesEmailWhichTheDispatcherSends() throws Exception {
    String target = roleUserId("COMPLIANCE_OFFICER");
    when(email.send(anyString(), anyString(), anyString())).thenReturn(SendResult.sent());
    changeRoles(target, "COMPLIANCE_OFFICER");

    String bearer = bearerForRole("COMPLIANCE_OFFICER");
    mvc.perform(get("/v1/notifications?unreadOnly=true").header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].type").value("ROLES_CHANGED"))
        .andExpect(jsonPath("$.data.content[0].message").value("Your roles are now: COMPLIANCE OFFICER."));

    assertThat(sql("select status from notifications.delivery_log where user_id = '" + target
        + "' and notification_type = 'ROLES_CHANGED' order by created_at desc limit 1")).isEqualTo("pending");
    dispatcher.runOnce();
    assertThat(sql("select status from notifications.delivery_log where user_id = '" + target
        + "' and notification_type = 'ROLES_CHANGED' order by created_at desc limit 1")).isEqualTo("sent");
  }

  @Test
  void failedEmailIsRetriedWithBackoffAndUnconfiguredIsSkipped() throws Exception {
    String target = roleUserId("CONSULTANT");
    when(email.send(anyString(), anyString(), anyString())).thenReturn(SendResult.failed("SMTP down"));
    changeRoles(target, "CONSULTANT");
    dispatcher.runOnce();
    String row = sql("select status || '|' || attempts || '|' || (next_attempt_at > now()) || '|' || last_error from notifications.delivery_log "
        + "where user_id = '" + target + "' order by created_at desc limit 1");
    assertThat(row).isEqualTo("pending|1|true|SMTP down");

    when(email.send(anyString(), anyString(), anyString())).thenReturn(SendResult.notConfigured("Email is not configured"));
    changeRoles(target, "CONSULTANT");
    dispatcher.runOnce();
    assertThat(sql("select status from notifications.delivery_log where user_id = '" + target
        + "' order by created_at desc limit 1")).isEqualTo("skipped");
  }

  @Test
  void preferencesTurnChannelsOffAndUsersOnlySeeTheirOwnNotifications() throws Exception {
    String target = roleUserId("INVESTOR_VIEWER");
    String bearer = bearerForRole("INVESTOR_VIEWER");
    mvc.perform(put("/v1/notifications/preferences").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"preferences\":[{\"type\":\"ROLES_CHANGED\",\"channel\":\"in_app\",\"enabled\":false}]}"))
        .andExpect(status().isOk());
    mvc.perform(get("/v1/notifications/preferences").header("Authorization", bearer))
        .andExpect(jsonPath("$.data[?(@.type=='ROLES_CHANGED' && @.channel=='in_app')].enabled").value(false))
        .andExpect(jsonPath("$.data[?(@.type=='ROLES_CHANGED' && @.channel=='sms')].sandbox").value(true));

    long before = json.readTree(mvc.perform(get("/v1/notifications/unread-count").header("Authorization", bearer))
        .andReturn().getResponse().getContentAsString()).path("data").path("unread").asLong();
    changeRoles(target, "INVESTOR_VIEWER");
    mvc.perform(get("/v1/notifications/unread-count").header("Authorization", bearer))
        .andExpect(jsonPath("$.data.unread").value(before));

    // Another user's notification id is invisible (404), and mark-all-read works.
    String someoneElses = sql("select id from notifications.notification_queue where user_id <> '" + target + "' limit 1");
    if (someoneElses != null) {
      mvc.perform(put("/v1/notifications/" + someoneElses + "/read").header("Authorization", bearer)).andExpect(status().isNotFound());
    }
    mvc.perform(put("/v1/notifications/read-all").header("Authorization", bearer)).andExpect(status().isOk());
    mvc.perform(get("/v1/notifications/unread-count").header("Authorization", bearer)).andExpect(jsonPath("$.data.unread").value(0));
  }
}
