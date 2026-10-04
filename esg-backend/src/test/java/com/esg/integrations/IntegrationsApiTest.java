package com.esg.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * WP-46 (D44): connectors on request (tenant request → platform queue → status), API keys (scoped, hashed, revocable,
 * audited as their creator), signed webhooks from the transactional outbox with retries, and the source templates.
 */
class IntegrationsApiTest extends ApiTestSupport {

  private static final AtomicInteger DAY = new AtomicInteger();

  @Autowired private ObjectMapper json;
  @Autowired private WebhookDispatcher dispatcher;

  private HttpServer receiver;
  private final List<Map<String, String>> received = new CopyOnWriteArrayList<>();
  private final AtomicInteger answer = new AtomicInteger(200);

  @BeforeEach
  void startReceiver() throws Exception {
    receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    receiver.createContext("/hook", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      received.add(Map.of("body", body, "event", String.valueOf(exchange.getRequestHeaders().getFirst("X-Atheris-Event")),
          "delivery", String.valueOf(exchange.getRequestHeaders().getFirst("X-Atheris-Delivery")),
          "signature", String.valueOf(exchange.getRequestHeaders().getFirst("X-Atheris-Signature"))));
      exchange.sendResponseHeaders(answer.get(), -1);
      exchange.close();
    });
    receiver.start();
  }

  @AfterEach
  void stopReceiver() {
    receiver.stop(0);
  }

  private String hookUrl() {
    return "http://127.0.0.1:" + receiver.getAddress().getPort() + "/hook";
  }

  private String admin() {
    return bearerForRole("TENANT_ADMIN");
  }

  private JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String who, String body)
      throws Exception {
    return mvc.perform(req.header("Authorization", who).contentType(MediaType.APPLICATION_JSON).content(body == null ? "{}" : body));
  }

  private static String day() {
    return LocalDate.of(2025, 9, 1).plusDays(DAY.getAndIncrement()).toString();
  }

  // ---------------------------------------------------------------- connectors on request

  @Test
  void aClientRequestsAConnectorAndThePlatformTeamActivatesIt() throws Exception {
    JsonNode catalogue = data(mvc.perform(get("/v1/integrations/connectors").header("Authorization", admin())).andExpect(status().isOk()));
    JsonNode sage = null;
    for (JsonNode c : catalogue) {
      if ("sage".equals(c.path("key").asText())) {
        sage = c;
      }
      if ("api_push".equals(c.path("key").asText())) {
        assertThat(c.path("availability").asText()).isEqualTo("available");
      }
    }
    assertThat(sage).isNotNull();
    assertThat(sage.path("availability").asText()).isEqualTo("on_request");

    String body = "{\"connectorKey\":\"sage\",\"systemDetails\":\"Sage 300 2024, on-premise\",\"dataNeeded\":[\"procurement_spend\",\"utilities\"],"
        + "\"frequency\":\"monthly\",\"contactName\":\"Ifeoma Finance\",\"contactEmail\":\"ifeoma@example.org\",\"contactPhone\":\"+234 801 234 5678\"}";
    JsonNode request = data(send(post("/v1/integrations/requests"), admin(), body).andExpect(status().isCreated()));
    assertThat(request.path("reference").asText()).matches("INT-\\d{4}-\\d{4}");
    assertThat(request.path("status").asText()).isEqualTo("requested");
    assertThat(request.path("connectorName").asText()).isEqualTo("Sage (50, 300, Intacct)");
    // One open request per connector; "available" ones need no request; the form is validated.
    send(post("/v1/integrations/requests"), admin(), body).andExpect(status().isConflict());
    send(post("/v1/integrations/requests"), admin(), body.replace("\"sage\"", "\"csv_templates\""))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.connectorKey").value("is available now — no request is needed"));
    send(post("/v1/integrations/requests"), admin(), body.replace("ifeoma@example.org", "not-an-email").replace("[\"procurement_spend\",\"utilities\"]", "[]"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.contactEmail").exists()).andExpect(jsonPath("$.errors.dataNeeded").exists());
    // Only administrators handle integrations.
    send(post("/v1/integrations/requests"), bearerForRole("ESG_ANALYST"), body).andExpect(status().isForbidden());

    // The platform team sees it in the queue with the tenant's name; a tenant administrator can't open the queue.
    String platform = bearerForRole("SUPER_ADMIN");
    mvc.perform(get("/v1/platform/integration-requests").header("Authorization", admin())).andExpect(status().isForbidden());
    JsonNode queued = null;
    for (JsonNode r : data(mvc.perform(get("/v1/platform/integration-requests").header("Authorization", platform)).andExpect(status().isOk()))) {
      if (r.path("id").asText().equals(request.path("id").asText())) {
        queued = r;
      }
    }
    assertThat(queued).isNotNull();
    assertThat(queued.path("tenantName").asText()).isEqualTo("Tenant A");
    String id = request.path("id").asText();
    send(post("/v1/platform/integration-requests/" + id + "/status"), platform, "{\"status\":\"declined\",\"version\":" + queued.path("version").asLong() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.note").exists());
    JsonNode review = data(send(post("/v1/platform/integration-requests/" + id + "/status"), platform,
        "{\"status\":\"in_review\",\"note\":\"We'll contact Ifeoma for sandbox access\",\"version\":" + queued.path("version").asLong() + "}")
        .andExpect(status().isOk()));
    send(post("/v1/platform/integration-requests/" + id + "/status"), platform,
        "{\"status\":\"active\",\"version\":" + queued.path("version").asLong() + "}").andExpect(status().isConflict());
    data(send(post("/v1/platform/integration-requests/" + id + "/status"), platform,
        "{\"status\":\"active\",\"note\":\"Live from 1 November\",\"version\":" + review.path("version").asLong() + "}").andExpect(status().isOk()));

    // The client sees the status; an active connector can't be withdrawn; tenant B sees nothing of it.
    JsonNode mine = data(mvc.perform(get("/v1/integrations/requests").header("Authorization", admin())));
    JsonNode active = null;
    for (JsonNode r : mine) {
      if (r.path("id").asText().equals(id)) {
        active = r;
      }
    }
    assertThat(active.path("status").asText()).isEqualTo("active");
    assertThat(active.path("statusNote").asText()).isEqualTo("Live from 1 November");
    mvc.perform(post("/v1/integrations/requests/" + id + "/withdraw?version=" + active.path("version").asLong()).header("Authorization", admin()))
        .andExpect(status().isConflict());
    assertThat(data(mvc.perform(get("/v1/integrations/requests").header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B))))
        .toString()).doesNotContain(id);
  }

  @Test
  void aRequestCanBeWithdrawnBeforeItIsActive() throws Exception {
    String body = "{\"connectorKey\":\"seamlesshr\",\"systemDetails\":\"SeamlessHR cloud\",\"dataNeeded\":[\"workforce\"],"
        + "\"frequency\":\"quarterly\",\"contactName\":\"Bisi HR\",\"contactEmail\":\"bisi@example.org\"}";
    JsonNode r = data(send(post("/v1/integrations/requests"), admin(), body).andExpect(status().isCreated()));
    JsonNode withdrawn = data(mvc.perform(post("/v1/integrations/requests/" + r.path("id").asText() + "/withdraw?version=" + r.path("version").asLong())
        .header("Authorization", admin())).andExpect(status().isOk()));
    assertThat(withdrawn.path("status").asText()).isEqualTo("withdrawn");
    // Withdrawn requests don't block a new one.
    send(post("/v1/integrations/requests"), admin(), body).andExpect(status().isCreated());
  }

  // ---------------------------------------------------------------- API keys

  @Test
  void anApiKeyActsWithinItsScopesAsItsCreatorAndCanBeRevoked() throws Exception {
    send(post("/v1/integrations/api-keys"), admin(), "{\"name\":\"Bad\",\"scopes\":[\"users:read\"]}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.scopes").value("an API key can't carry users:read"));
    send(post("/v1/integrations/api-keys"), admin(), "{\"name\":\"Past\",\"scopes\":[\"data_points:read\"],\"expiresAt\":\"2020-01-01T00:00:00Z\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.expiresAt").exists());
    JsonNode created = data(send(post("/v1/integrations/api-keys"), admin(),
        "{\"name\":\"Flow station gateway\",\"scopes\":[\"data_points:create\",\"data_points:read\",\"reference_data:read\"]}")
        .andExpect(status().isCreated()));
    String key = created.path("key").asText();
    assertThat(key).startsWith("atk_" + created.path("apiKey").path("prefix").asText() + "_");
    assertThat(created.path("apiKey").path("status").asText()).isEqualTo("active");
    // Listed without the secret, and only its hash is stored.
    String list = data(mvc.perform(get("/v1/integrations/api-keys").header("Authorization", admin()))).toString();
    assertThat(list).contains("Flow station gateway").doesNotContain(key);
    assertThat(count("select count(*) from integrations.api_keys where key_hash = encode(sha256(convert_to('" + key + "', 'UTF8')), 'hex')"))
        .isEqualTo(1);

    // The gateway reads and sends data points with the key alone.
    mvc.perform(get("/v1/data/points?size=1").header("X-API-Key", key)).andExpect(status().isOk());
    String d = day();
    JsonNode point = data(mvc.perform(post("/v1/data/points").header("Authorization", "ApiKey " + key).contentType(MediaType.APPLICATION_JSON)
        .content("{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"waste_disposed_t\",\"numericValue\":42,\"reportingPeriodStart\":\"" + d
            + "\",\"reportingPeriodEnd\":\"" + d + "\"}")).andExpect(status().isCreated()));
    assertThat(point.path("validationStatus").asText()).isEqualTo("draft");
    assertThat(point.path("createdBy").asText()).isEqualTo(roleUserId("TENANT_ADMIN"));
    // The audit trail names the key as the client.
    assertThat(count("select count(*) from audit.audit_logs where resource = 'data_hub.data_points' and action = 'insert' and resource_id = '"
        + point.path("id").asText() + "' and user_agent like 'API key Flow station gateway (%'")).isEqualTo(1);
    // Nothing outside its scopes, even though its creator is an administrator; it can't manage keys either.
    mvc.perform(get("/v1/admin/users").header("X-API-Key", key)).andExpect(status().isForbidden());
    mvc.perform(get("/v1/integrations/api-keys").header("X-API-Key", key)).andExpect(status().isForbidden());
    // It may send its readings for validation (data_points:create), never validate or approve them.
    mvc.perform(put("/v1/data/points/" + point.path("id").asText() + "/submit").header("X-API-Key", key).contentType(MediaType.APPLICATION_JSON)
        .content("{\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.validationStatus").value("submitted"));
    mvc.perform(put("/v1/data/points/" + point.path("id").asText() + "/validate").header("X-API-Key", key).contentType(MediaType.APPLICATION_JSON)
        .content("{\"version\":1}")).andExpect(status().isForbidden());

    // A wrong secret, a revoked key and an expired key are refused.
    mvc.perform(get("/v1/data/points").header("X-API-Key", key.substring(0, key.length() - 2) + "zz")).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("ESG-401-API-KEY"));
    mvc.perform(get("/v1/data/points").header("X-API-Key", "atk_nosuch_secret")).andExpect(status().isUnauthorized());
    String id = created.path("apiKey").path("id").asText();
    data(mvc.perform(post("/v1/integrations/api-keys/" + id + "/revoke").header("Authorization", admin())).andExpect(status().isOk()));
    mvc.perform(get("/v1/data/points").header("X-API-Key", key)).andExpect(status().isUnauthorized());
    mvc.perform(post("/v1/integrations/api-keys/" + id + "/revoke").header("Authorization", admin())).andExpect(status().isConflict());

    JsonNode other = data(send(post("/v1/integrations/api-keys"), admin(), "{\"name\":\"Expiring\",\"scopes\":[\"data_points:read\"]}"));
    asOwnerInTenantA("update integrations.api_keys set expires_at = now() - interval '1 minute' where id = '" + other.path("apiKey").path("id").asText() + "'");
    mvc.perform(get("/v1/data/points").header("X-API-Key", other.path("key").asText())).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------- webhooks

  @Test
  void anApprovedDataPointIsSentSignedToTheSubscribedEndpoint() throws Exception {
    send(post("/v1/integrations/webhooks"), admin(), "{\"name\":\"x\",\"url\":\"" + hookUrl() + "\",\"events\":[\"nope\"]}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.events").value("unknown event: nope"));
    JsonNode created = data(send(post("/v1/integrations/webhooks"), admin(),
        "{\"name\":\"ERP bridge\",\"url\":\"" + hookUrl() + "\",\"events\":[\"data_point.approved\"]}").andExpect(status().isCreated()));
    String secret = created.path("secret").asText();
    assertThat(secret).startsWith("whsec_");
    String hook = created.path("webhook").path("id").asText();
    assertThat(data(mvc.perform(get("/v1/integrations/webhooks").header("Authorization", admin()))).toString()).doesNotContain(secret);

    // A data point goes through the workflow; approval queues the event in the same transaction.
    String collector = bearerForRole("DATA_COLLECTOR");
    String d = day();
    JsonNode p = data(send(post("/v1/data/points"), collector, "{\"orgId\":\"" + ORG_SITE_1 + "\",\"metricKey\":\"waste_disposed_t\",\"numericValue\":75,"
        + "\"reportingPeriodStart\":\"" + d + "\",\"reportingPeriodEnd\":\"" + d + "\"}").andExpect(status().isCreated()));
    p = data(send(put("/v1/data/points/" + p.path("id").asText() + "/submit"), collector, "{\"version\":" + p.path("version").asLong() + "}"));
    p = data(send(put("/v1/data/points/" + p.path("id").asText() + "/validate"), bearerForRole("ESG_ANALYST"), "{\"version\":" + p.path("version").asLong() + "}"));
    // A refused approval (the validator can't approve) queues nothing.
    int before = count("select count(*) from integrations.webhook_deliveries where endpoint_id = '" + hook + "'");
    send(put("/v1/data/points/" + p.path("id").asText() + "/approve"), bearerForRole("ESG_ANALYST"), "{\"version\":" + p.path("version").asLong() + "}")
        .andExpect(status().isForbidden());
    assertThat(count("select count(*) from integrations.webhook_deliveries where endpoint_id = '" + hook + "'")).isEqualTo(before);
    send(put("/v1/data/points/" + p.path("id").asText() + "/approve"), bearerForRole("ESG_DIRECTOR"), "{\"version\":" + p.path("version").asLong() + "}")
        .andExpect(status().isOk());
    assertThat(count("select count(*) from integrations.webhook_deliveries where endpoint_id = '" + hook + "' and status = 'pending'")).isEqualTo(1);

    dispatcher.dispatchAll();
    assertThat(received).hasSize(1);
    Map<String, String> got = received.get(0);
    assertThat(got.get("event")).isEqualTo("data_point.approved");
    JsonNode payload = json.readTree(got.get("body"));
    assertThat(payload.path("type").asText()).isEqualTo("data_point.approved");
    assertThat(payload.path("data").path("id").asText()).isEqualTo(p.path("id").asText());
    assertThat(payload.path("data").path("metricKey").asText()).isEqualTo("waste_disposed_t");
    assertThat(payload.path("data").path("validationStatus").asText()).isEqualTo("approved");
    // The receiver can check the signature with its secret.
    String[] sig = got.get("signature").split(",");
    String t = sig[0].substring(2);
    assertThat(sig[1]).isEqualTo("v1=" + WebhookDispatcher.hmac(secret, t + "." + got.get("body")));

    JsonNode deliveries = data(mvc.perform(get("/v1/integrations/webhooks/" + hook + "/deliveries").header("Authorization", admin())));
    assertThat(deliveries.get(0).path("status").asText()).isEqualTo("delivered");
    assertThat(deliveries.get(0).path("lastStatusCode").asInt()).isEqualTo(200);
    assertThat(deliveries.get(0).path("id").asText()).isEqualTo(got.get("delivery"));
    // Nothing is sent twice.
    dispatcher.dispatchAll();
    assertThat(received).hasSize(1);
  }

  @Test
  void aFailingEndpointIsRetriedAndTheTestEventReportsTheOutcome() throws Exception {
    JsonNode created = data(send(post("/v1/integrations/webhooks"), admin(),
        "{\"name\":\"Flaky\",\"url\":\"" + hookUrl() + "\",\"events\":[\"safety_incident.recorded\"]}"));
    String hook = created.path("webhook").path("id").asText();
    answer.set(500);
    JsonNode test = data(mvc.perform(post("/v1/integrations/webhooks/" + hook + "/test").header("Authorization", admin())).andExpect(status().isOk()));
    assertThat(test.path("status").asText()).isEqualTo("pending");
    assertThat(test.path("attempts").asInt()).isEqualTo(1);
    assertThat(test.path("lastStatusCode").asInt()).isEqualTo(500);
    assertThat(test.path("lastError").asText()).isEqualTo("The endpoint answered HTTP 500");
    // The next attempt waits (1 minute after the first failure); fixed, it can be redelivered at once.
    dispatcher.dispatchAll();
    assertThat(received).hasSize(1);
    answer.set(204);
    JsonNode again = data(mvc.perform(post("/v1/integrations/webhook-deliveries/" + test.path("id").asText() + "/redeliver")
        .header("Authorization", admin())).andExpect(status().isOk()));
    assertThat(again.path("status").asText()).isEqualTo("delivered");
    assertThat(received.get(1).get("event")).isEqualTo("ping");

    // An incident report reaches it too; rotating the secret signs with the new one; removing it stops deliveries.
    JsonNode rotated = data(mvc.perform(post("/v1/integrations/webhooks/" + hook + "/rotate-secret").header("Authorization", admin())));
    assertThat(rotated.path("secret").asText()).isNotEqualTo(created.path("secret").asText());
    send(post("/v1/social/incidents"), bearerForRole("ESG_MANAGER"), "{\"orgId\":\"" + ORG_SITE_1 + "\",\"title\":\"Webhook near miss\","
        + "\"incidentType\":\"near_miss\",\"outcome\":\"none\",\"occurredAt\":\"2026-09-30T08:15\"}").andExpect(status().isCreated());
    dispatcher.dispatchAll();
    Map<String, String> incident = received.get(received.size() - 1);
    assertThat(incident.get("event")).isEqualTo("safety_incident.recorded");
    String t = incident.get("signature").split(",")[0].substring(2);
    assertThat(incident.get("signature")).endsWith("v1=" + WebhookDispatcher.hmac(rotated.path("secret").asText(), t + "." + incident.get("body")));
    long version = rotated.path("webhook").path("version").asLong();
    mvc.perform(delete("/v1/integrations/webhooks/" + hook + "?version=" + version).header("Authorization", admin())).andExpect(status().isNoContent());
    mvc.perform(get("/v1/integrations/webhooks/" + hook + "/deliveries").header("Authorization", admin())).andExpect(status().isNotFound());
  }

  @Test
  void anEndpointThatKeepsFailingIsSwitchedOff() throws Exception {
    JsonNode created = data(send(post("/v1/integrations/webhooks"), admin(),
        "{\"name\":\"Gone\",\"url\":\"" + hookUrl() + "\",\"events\":[\"report.approved\"]}"));
    String hook = created.path("webhook").path("id").asText();
    asOwnerInTenantA("update integrations.webhook_endpoints set consecutive_failures = " + (WebhookDispatcher.DISABLE_AFTER - 1) + " where id = '" + hook + "'");
    answer.set(410);
    mvc.perform(post("/v1/integrations/webhooks/" + hook + "/test").header("Authorization", admin())).andExpect(status().isOk());
    JsonNode after = null;
    for (JsonNode w : data(mvc.perform(get("/v1/integrations/webhooks").header("Authorization", admin())))) {
      if (w.path("id").asText().equals(hook)) {
        after = w;
      }
    }
    assertThat(after.path("active").asBoolean()).isFalse();
    assertThat(after.path("disabledReason").asText()).isEqualTo("Switched off after 20 failed deliveries in a row");
    // Switched back on, the failure count starts again.
    JsonNode on = data(send(put("/v1/integrations/webhooks/" + hook), admin(), "{\"name\":\"Gone\",\"url\":\"" + hookUrl()
        + "\",\"events\":[\"report.approved\"],\"active\":true,\"version\":" + after.path("version").asLong() + "}").andExpect(status().isOk()));
    assertThat(on.path("consecutiveFailures").asInt()).isZero();
    assertThat(on.path("disabledReason").isMissingNode() || on.path("disabledReason").isNull()).isTrue();
  }

  // ---------------------------------------------------------------- source templates

  @Test
  void eachSourceSystemHasAnImportTemplate() throws Exception {
    String workforce = mvc.perform(get("/v1/data/import/template?kind=workforce").header("Authorization", bearerForRole("DATA_COLLECTOR")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(workforce.lines().findFirst().orElseThrow()).isEqualTo("organisation,metric,value,currency,period_start,period_end,sub_category,notes,owner");
    assertThat(workforce).contains(",employees_total,,,").contains("From HR system headcount at period end");
    String utilities = mvc.perform(get("/v1/data/import/template?kind=utilities").header("Authorization", bearerForRole("DATA_COLLECTOR")))
        .andReturn().getResponse().getContentAsString();
    assertThat(utilities).contains(",electricity_kwh,").contains(",water_withdrawal_m3,");
    mvc.perform(get("/v1/data/import/template?kind=payroll").header("Authorization", bearerForRole("DATA_COLLECTOR")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.kind").value("must be one of general, energy, utilities, waste, workforce"));
  }

  private static int count(String sql) throws Exception {
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      ResultSet rs = st.executeQuery(sql);
      rs.next();
      return rs.getInt(1);
    }
  }
}
