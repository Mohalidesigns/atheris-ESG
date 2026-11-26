package com.esg.datahub;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * D51: the Data Hub workflow tells the people who must act. Submitted → validators whose scope covers the point;
 * validated → approvers other than the submitter and validator (administrators only when nobody else can approve);
 * rejected → the submitter, with the reason. A bulk action sends one notice per person with the number of points.
 * Fixture scopes: ESG_MANAGER, ESG_ANALYST and DATA_COLLECTOR to Site 1; ESG_DIRECTOR tenant-wide.
 */
class DataPointNotificationTest extends ApiTestSupport {

  /** One-day periods in 2026 that no other test uses, so the duplicate check never trips. */
  private static final AtomicInteger DAY = new AtomicInteger();

  private static final String COLLECTOR = roleUserId("DATA_COLLECTOR");
  private static final String ANALYST = roleUserId("ESG_ANALYST");
  private static final String MANAGER = roleUserId("ESG_MANAGER");
  private static final String DIRECTOR = roleUserId("ESG_DIRECTOR");
  private static final String TENANT_ADMIN = roleUserId("TENANT_ADMIN");

  @Autowired private ObjectMapper json;

  @Test
  void aSubmittedPointNotifiesTheValidatorsWhoseScopeCoversItButNotTheSubmitter() throws Exception {
    long analyst = inbox(ANALYST, "DATA_VALIDATION_REQUESTED");
    long manager = inbox(MANAGER, "DATA_VALIDATION_REQUESTED");
    long director = inbox(DIRECTOR, "DATA_VALIDATION_REQUESTED");
    long collector = inbox(COLLECTOR, "DATA_VALIDATION_REQUESTED");
    long admin = inbox(TENANT_ADMIN, "DATA_VALIDATION_REQUESTED");
    long emails = emails(ANALYST, "DATA_VALIDATION_REQUESTED");

    submit("DATA_COLLECTOR", point("DATA_COLLECTOR", ORG_SITE_1));

    assertThat(inbox(ANALYST, "DATA_VALIDATION_REQUESTED")).isEqualTo(analyst + 1);
    assertThat(inbox(MANAGER, "DATA_VALIDATION_REQUESTED")).isEqualTo(manager + 1);
    assertThat(inbox(DIRECTOR, "DATA_VALIDATION_REQUESTED")).isEqualTo(director + 1);
    assertThat(inbox(COLLECTOR, "DATA_VALIDATION_REQUESTED")).as("the submitter").isEqualTo(collector);
    assertThat(inbox(TENANT_ADMIN, "DATA_VALIDATION_REQUESTED")).as("admins only as a fallback").isEqualTo(admin);
    assertThat(emails(ANALYST, "DATA_VALIDATION_REQUESTED")).isEqualTo(emails + 1);
    assertThat(lastEmailBody(ANALYST, "DATA_VALIDATION_REQUESTED")).contains("waiting for your validation").contains(": 1.").contains("/data-hub");
  }

  @Test
  void validatorsScopedToAnotherSiteAreNotTold() throws Exception {
    long analyst = inbox(ANALYST, "DATA_VALIDATION_REQUESTED");
    long manager = inbox(MANAGER, "DATA_VALIDATION_REQUESTED");

    submit("ESG_DIRECTOR", point("ESG_DIRECTOR", ORG_SITE_2));

    assertThat(inbox(ANALYST, "DATA_VALIDATION_REQUESTED")).isEqualTo(analyst);
    assertThat(inbox(MANAGER, "DATA_VALIDATION_REQUESTED")).isEqualTo(manager);
  }

  @Test
  void aValidatedPointNotifiesApproversOtherThanTheSubmitterAndValidator() throws Exception {
    JsonNode submitted = submit("DATA_COLLECTOR", point("DATA_COLLECTOR", ORG_SITE_1));
    long director = inbox(DIRECTOR, "DATA_APPROVAL_REQUESTED");
    long manager = inbox(MANAGER, "DATA_APPROVAL_REQUESTED");
    long collector = inbox(COLLECTOR, "DATA_APPROVAL_REQUESTED");

    step("ESG_MANAGER", "validate", submitted, null).andExpect(status().isOk());

    assertThat(inbox(DIRECTOR, "DATA_APPROVAL_REQUESTED")).isEqualTo(director + 1);
    assertThat(inbox(MANAGER, "DATA_APPROVAL_REQUESTED")).as("the validator").isEqualTo(manager);
    assertThat(inbox(COLLECTOR, "DATA_APPROVAL_REQUESTED")).as("the submitter").isEqualTo(collector);
  }

  @Test
  void aDirectorScopedToAParentOrganisationIsTold() throws Exception {
    JsonNode submitted = submit("DATA_COLLECTOR", point("DATA_COLLECTOR", ORG_SITE_1));
    long hqDirector = inbox(USER_A, "DATA_APPROVAL_REQUESTED"); // ESG_DIRECTOR scoped to HQ, above Site 1

    step("ESG_MANAGER", "validate", submitted, null).andExpect(status().isOk());

    assertThat(inbox(USER_A, "DATA_APPROVAL_REQUESTED")).isEqualTo(hqDirector + 1);
  }

  @Test
  void whenTheOnlyApproverValidatedItTheAdministratorsAreTold() throws Exception {
    JsonNode submitted = submit("DATA_COLLECTOR", point("DATA_COLLECTOR", ORG_SITE_1));
    long director = inbox(DIRECTOR, "DATA_APPROVAL_REQUESTED");
    long admin = inbox(TENANT_ADMIN, "DATA_APPROVAL_REQUESTED");
    // Leave the tenant-wide director as the only approver: an expired role assignment does not count.
    asOwnerInTenantA("update auth.user_roles set expires_at = now() - interval '1 minute' where user_id = '" + USER_A + "'");
    try {
      step("ESG_DIRECTOR", "validate", submitted, null).andExpect(status().isOk());
    } finally {
      asOwnerInTenantA("update auth.user_roles set expires_at = null where user_id = '" + USER_A + "'");
    }

    assertThat(inbox(DIRECTOR, "DATA_APPROVAL_REQUESTED")).as("the validator may not approve").isEqualTo(director);
    assertThat(inbox(TENANT_ADMIN, "DATA_APPROVAL_REQUESTED")).isEqualTo(admin + 1);
  }

  @Test
  void aRejectedPointGoesBackToTheSubmitterWithTheReason() throws Exception {
    JsonNode submitted = submit("DATA_COLLECTOR", point("DATA_COLLECTOR", ORG_SITE_1));
    long collector = inbox(COLLECTOR, "DATA_POINTS_REJECTED");
    long analyst = inbox(ANALYST, "DATA_POINTS_REJECTED");

    step("ESG_ANALYST", "reject", submitted, "Litres, not cubic metres").andExpect(status().isOk());

    assertThat(inbox(COLLECTOR, "DATA_POINTS_REJECTED")).isEqualTo(collector + 1);
    assertThat(inbox(ANALYST, "DATA_POINTS_REJECTED")).as("the person who rejected it").isEqualTo(analyst);
    assertThat(lastInboxMessage(COLLECTOR, "DATA_POINTS_REJECTED")).contains("Litres, not cubic metres");
  }

  @Test
  void aBulkSubmitSendsEachValidatorOneNoticeWithTheCount() throws Exception {
    List<JsonNode> drafts = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      drafts.add(point("DATA_COLLECTOR", ORG_SITE_1));
    }
    long analyst = inbox(ANALYST, "DATA_VALIDATION_REQUESTED");
    long emails = emails(ANALYST, "DATA_VALIDATION_REQUESTED");

    StringBuilder items = new StringBuilder();
    for (JsonNode d : drafts) {
      items.append(items.length() == 0 ? "" : ",").append("{\"id\":\"").append(d.path("id").asText())
          .append("\",\"version\":").append(d.path("version").asLong()).append('}');
    }
    mvc.perform(post("/v1/data/points/bulk").header("Authorization", bearerForRole("DATA_COLLECTOR"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"submit\",\"items\":[" + items + "]}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.succeeded").value(3));

    assertThat(inbox(ANALYST, "DATA_VALIDATION_REQUESTED")).isEqualTo(analyst + 1);
    assertThat(emails(ANALYST, "DATA_VALIDATION_REQUESTED")).isEqualTo(emails + 1);
    assertThat(lastInboxMessage(ANALYST, "DATA_VALIDATION_REQUESTED")).contains(": 3.");
  }

  // ---------------------------------------------------------------- helpers

  private JsonNode data(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
  }

  private JsonNode point(String role, String org) throws Exception {
    String day = LocalDate.of(2026, 10, 1).plusDays(DAY.getAndIncrement()).toString();
    String body = "{\"orgId\":\"" + org + "\",\"metricKey\":\"diesel_litres\",\"numericValue\":42,"
        + "\"reportingPeriodStart\":\"" + day + "\",\"reportingPeriodEnd\":\"" + day + "\"}";
    return data(mvc.perform(post("/v1/data/points").header("Authorization", bearerForRole(role))
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()));
  }

  private JsonNode submit(String role, JsonNode draft) throws Exception {
    return data(step(role, "submit", draft, null).andExpect(status().isOk()));
  }

  private ResultActions step(String role, String action, JsonNode point, String reason) throws Exception {
    String body = "{\"version\":" + point.path("version").asLong() + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}";
    return mvc.perform(put("/v1/data/points/" + point.path("id").asText() + "/" + action).header("Authorization", bearerForRole(role))
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static long inbox(String userId, String type) throws Exception {
    return count("select count(*) from notifications.notification_queue where user_id = '" + userId
        + "' and notification_type = '" + type + "'");
  }

  private static long emails(String userId, String type) throws Exception {
    return count("select count(*) from notifications.delivery_log where user_id = '" + userId
        + "' and notification_type = '" + type + "' and channel = 'email'");
  }

  private static String lastInboxMessage(String userId, String type) throws Exception {
    return text("select message from notifications.notification_queue where user_id = '" + userId
        + "' and notification_type = '" + type + "' order by created_at desc limit 1");
  }

  private static String lastEmailBody(String userId, String type) throws Exception {
    return text("select body from notifications.delivery_log where user_id = '" + userId
        + "' and notification_type = '" + type + "' and channel = 'email' order by created_at desc limit 1");
  }

  private static long count(String sql) throws Exception {
    return Long.parseLong(text(sql));
  }

  private static String text(String sql) throws Exception {
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      ResultSet rs = st.executeQuery(sql);
      rs.next();
      return rs.getString(1);
    }
  }
}
