package com.esg.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * WP-43 offline sync (TRD §8.1 OfflineSyncResolver, TRD §11.2.2-01/-03, §11.2.4-05, journey 26 at API level): idempotent
 * operations, the conflict key (tenant, org, metric_key, period), last write wins with manual override, per-operation
 * validation, period locks, incidents, and 500 records in under 30 seconds.
 */
class OfflineSyncApiTest extends ApiTestSupport {

  /** Distinct one-day petrol periods in 2025 (other suites use petrol in 2026) so tests never meet each other by accident. */
  private static final AtomicInteger DAY = new AtomicInteger();

  @Autowired private ObjectMapper json;

  private static String nextDay() {
    return LocalDate.of(2025, 2, 1).plusDays(DAY.getAndIncrement()).toString();
  }

  private static String point(String org, String metric, String day, Object value) {
    return "{\"orgId\":\"" + org + "\",\"metricKey\":\"" + metric + "\",\"numericValue\":" + value
        + ",\"reportingPeriodStart\":\"" + day + "\",\"reportingPeriodEnd\":\"" + day + "\",\"notes\":\"Captured offline\"}";
  }

  private static String op(UUID opId, UUID clientId, Instant capturedAt, String recordId, Long baseVersion, String dataPoint) {
    return "{\"opId\":\"" + opId + "\",\"clientId\":\"" + clientId + "\",\"kind\":\"data_point\",\"capturedAt\":\"" + capturedAt + "\""
        + (recordId == null ? "" : ",\"recordId\":\"" + recordId + "\",\"baseVersion\":" + baseVersion)
        + ",\"dataPoint\":" + dataPoint + "}";
  }

  private static String op(String dataPoint, Instant capturedAt) {
    return op(UUID.randomUUID(), UUID.randomUUID(), capturedAt, null, null, dataPoint);
  }

  private ResultActions send(String who, String device, List<String> ops) throws Exception {
    return mvc.perform(post("/v1/data/sync").header("Authorization", who).contentType(MediaType.APPLICATION_JSON)
        .content("{\"deviceId\":\"" + device + "\",\"operations\":[" + String.join(",", ops) + "]}"));
  }

  private JsonNode sync(String who, String device, List<String> ops) throws Exception {
    return data(send(who, device, ops).andExpect(status().isOk()));
  }

  private JsonNode data(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
  }

  private JsonNode get(String who, String path) throws Exception {
    return data(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header("Authorization", who))
        .andExpect(status().isOk()));
  }

  private ResultActions resolve(String who, String conflictId, String resolution, Long version) throws Exception {
    return mvc.perform(post("/v1/data/sync/conflicts/" + conflictId + "/resolve").header("Authorization", who)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"resolution\":\"" + resolution + "\"" + (version == null ? "" : ",\"version\":" + version) + "}"));
  }

  private static int count(String sql) throws Exception {
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      ResultSet rs = st.executeQuery(sql);
      rs.next();
      return rs.getInt(1);
    }
  }

  private String collector() {
    return bearerForRole("DATA_COLLECTOR"); // Site 1; data_points:create + update
  }

  private String analyst() {
    return bearerForRole("ESG_ANALYST"); // Site 1
  }

  @Test
  void hundredOfflinePointsSyncAndResendingThemChangesNothing() throws Exception {
    List<String> ops = new ArrayList<>();
    Instant captured = Instant.now().minus(2, ChronoUnit.HOURS);
    for (int i = 0; i < 100; i++) {
      ops.add(op(point(ORG_SITE_1, "petrol_litres", nextDay(), 100 + i), captured));
    }
    JsonNode first = sync(collector(), "phone-100", ops);
    assertThat(first.path("received").asInt()).isEqualTo(100);
    assertThat(first.path("applied").asInt()).isEqualTo(100);
    assertThat(first.path("results").get(0).path("recordId").asText()).isNotBlank();
    assertThat(first.path("results").get(0).path("replayed").asBoolean()).isFalse();
    int saved = count("select count(*) from data_hub.data_points where notes = 'Captured offline' and metric_key = 'petrol_litres' "
        + "and deleted_at is null");

    // The connection dropped before the device heard back: it sends the same operations again.
    JsonNode again = sync(collector(), "phone-100", ops);
    assertThat(again.path("applied").asInt()).isEqualTo(100);
    for (int i = 0; i < 100; i++) {
      assertThat(again.path("results").get(i).path("replayed").asBoolean()).isTrue();
      assertThat(again.path("results").get(i).path("recordId").asText()).isEqualTo(first.path("results").get(i).path("recordId").asText());
    }
    assertThat(count("select count(*) from data_hub.data_points where notes = 'Captured offline' and metric_key = 'petrol_litres' "
        + "and deleted_at is null")).isEqualTo(saved);

    // Saved as drafts through the normal rules: quality scored, owned by the collector, in the Data Hub.
    JsonNode created = get(collector(), "/v1/data/points/" + first.path("results").get(0).path("recordId").asText());
    assertThat(created.path("validationStatus").asText()).isEqualTo("draft");
    assertThat(created.path("numericValue").asDouble()).isEqualTo(100);
    assertThat(created.path("qualityScore").isNumber()).isTrue();
    assertThat(created.path("createdBy").asText()).isEqualTo(roleUserId("DATA_COLLECTOR"));
  }

  @Test
  void anOfflineEditOfASyncedRecordUpdatesItUnlessTheServerMovedOn() throws Exception {
    UUID client = UUID.randomUUID();
    String day = nextDay();
    Instant t0 = Instant.now().minus(3, ChronoUnit.HOURS);
    JsonNode r1 = sync(collector(), "phone-edit", List.of(op(UUID.randomUUID(), client, t0, null, null, point(ORG_SITE_1, "petrol_litres", day, 50))))
        .path("results").get(0);
    String id = r1.path("recordId").asText();

    // The device never heard back, so it edits the record without knowing its server id: same clientId → same record.
    JsonNode r2 = sync(collector(), "phone-edit", List.of(op(UUID.randomUUID(), client, t0.plusSeconds(60), null, null,
        point(ORG_SITE_1, "petrol_litres", day, 55)))).path("results").get(0);
    assertThat(r2.path("outcome").asText()).isEqualTo("applied");
    assertThat(r2.path("recordId").asText()).isEqualTo(id);
    assertThat(get(collector(), "/v1/data/points/" + id).path("numericValue").asDouble()).isEqualTo(55);

    // An edit made against an old version is a conflict, not a silent overwrite.
    JsonNode r3 = sync(collector(), "phone-edit", List.of(op(UUID.randomUUID(), client, t0.plusSeconds(120), id, r1.path("recordVersion").asLong(),
        point(ORG_SITE_1, "petrol_litres", day, 60)))).path("results").get(0);
    assertThat(r3.path("outcome").asText()).isEqualTo("conflict");
    assertThat(r3.path("message").asText()).isEqualTo("It was changed on the server after this device last saw it");
    assertThat(get(collector(), "/v1/data/points/" + id).path("numericValue").asDouble()).isEqualTo(55);
  }

  @Test
  void twoDevicesConflictOnTheSameMetricAndPeriodAndThePersonDecides() throws Exception {
    String day = nextDay();
    Instant earlier = Instant.now().minus(1, ChronoUnit.HOURS);
    // Device B (the analyst) syncs first; device A (the collector) captured its value earlier but syncs later.
    JsonNode b = sync(analyst(), "tablet-b", List.of(op(point(ORG_SITE_1, "petrol_litres", day, 700), Instant.now()))).path("results").get(0);
    assertThat(b.path("outcome").asText()).isEqualTo("applied");
    JsonNode a = sync(collector(), "phone-a", List.of(op(point(ORG_SITE_1, "petrol_litres", day, 710), earlier))).path("results").get(0);
    assertThat(a.path("outcome").asText()).isEqualTo("conflict");
    assertThat(a.path("recordId").asText()).isEqualTo(b.path("recordId").asText());
    assertThat(a.path("message").asText()).contains("after you captured yours");

    // The collector sees their value next to the server's.
    JsonNode conflicts = get(collector(), "/v1/data/sync/conflicts");
    JsonNode mine = null;
    for (JsonNode c : conflicts) {
      if (c.path("opId").asText().equals(a.path("opId").asText())) {
        mine = c;
      }
    }
    assertThat(mine).isNotNull();
    assertThat(mine.path("mine").path("numericValue").asDouble()).isEqualTo(710);
    assertThat(mine.path("server").path("numericValue").asDouble()).isEqualTo(700);
    assertThat(mine.path("deviceId").asText()).isEqualTo("phone-a");
    // Nobody else sees it, or can resolve it.
    assertThat(get(analyst(), "/v1/data/sync/conflicts").toString()).doesNotContain(mine.path("id").asText());
    resolve(analyst(), mine.path("id").asText(), "keep_server", null).andExpect(status().isNotFound());

    // Manual override: against a stale version it is refused; against the version shown it replaces the server value.
    long version = mine.path("server").path("version").asLong();
    resolve(collector(), mine.path("id").asText(), "use_mine", version + 5).andExpect(status().isConflict());
    resolve(collector(), mine.path("id").asText(), "use_mine", version).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.outcome").value("applied"));
    assertThat(get(collector(), "/v1/data/points/" + b.path("recordId").asText()).path("numericValue").asDouble()).isEqualTo(710);
    resolve(collector(), mine.path("id").asText(), "keep_server", null).andExpect(status().isConflict());
    assertThat(get(collector(), "/v1/data/sync/conflicts").toString()).doesNotContain(mine.path("id").asText());
  }

  @Test
  void lastWriteWinsWhenTheOfflineValueIsNewerThanAServerDraft() throws Exception {
    String day = nextDay();
    JsonNode server = sync(analyst(), "tablet-b", List.of(op(point(ORG_SITE_1, "petrol_litres", day, 300), Instant.now().minusSeconds(600))))
        .path("results").get(0);
    // The collector captured theirs after the server value was saved: it wins, and the old value stays in the audit trail.
    JsonNode later = sync(collector(), "phone-a", List.of(op(point(ORG_SITE_1, "petrol_litres", day, 320), Instant.now().plusSeconds(1))))
        .path("results").get(0);
    assertThat(later.path("outcome").asText()).isEqualTo("applied");
    assertThat(later.path("recordId").asText()).isEqualTo(server.path("recordId").asText());
    assertThat(later.path("message").asText()).startsWith("Replaced the value saved on the server on ");
    assertThat(get(collector(), "/v1/data/points/" + later.path("recordId").asText()).path("numericValue").asDouble()).isEqualTo(320);
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/data/points/" + later.path("recordId").asText() + "/history").header("Authorization", bearerForRole("AUDITOR")))
        .andExpect(jsonPath("$.data[*].action", org.hamcrest.Matchers.contains("created", "edited")));
  }

  @Test
  void aValueAlreadyInTheApprovalWorkflowIsNeverReplacedFromADevice() throws Exception {
    String day = nextDay();
    JsonNode server = sync(analyst(), "tablet-b", List.of(op(point(ORG_SITE_1, "petrol_litres", day, 400), Instant.now().minusSeconds(600))))
        .path("results").get(0);
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/v1/data/points/" + server.path("recordId").asText() + "/submit")
        .header("Authorization", analyst()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"version\":" + server.path("recordVersion").asLong() + "}")).andExpect(status().isOk());
    JsonNode later = sync(collector(), "phone-a", List.of(op(point(ORG_SITE_1, "petrol_litres", day, 420), Instant.now()))).path("results").get(0);
    assertThat(later.path("outcome").asText()).isEqualTo("conflict");
    assertThat(later.path("message").asText()).isEqualTo("A value for this metric and period is already submitted on the server");
    String conflictId = later.path("syncId").asText();
    long version = get(collector(), "/v1/data/points/" + server.path("recordId").asText()).path("version").asLong();
    resolve(collector(), conflictId, "use_mine", version).andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("The server value is submitted: it must be rejected or reopened before it can be replaced"));
    resolve(collector(), conflictId, "keep_server", null).andExpect(status().isOk()).andExpect(jsonPath("$.data.outcome").value("discarded"));
  }

  @Test
  void eachOperationIsValidatedOnItsOwnAndLockedPeriodsAreRefused() throws Exception {
    String bad = "{\"metricKey\":\"petrol_litres\",\"numericValue\":-5,\"reportingPeriodStart\":\"2026-02-01\",\"reportingPeriodEnd\":\"2026-02-01\"}";
    JsonNode result = sync(collector(), "phone-v", List.of(
        op(point(ORG_SITE_1, "petrol_litres", nextDay(), 10), Instant.now()),
        op(bad, Instant.now()),
        op(point(ORG_SITE_1, "petrol_litres", "2020-06-01", 10), Instant.now()),
        op(point(ORG_SITE_2, "petrol_litres", nextDay(), 10), Instant.now())));
    assertThat(result.path("applied").asInt()).isEqualTo(1);
    assertThat(result.path("rejected").asInt()).isEqualTo(3);
    assertThat(result.path("results").get(1).path("outcome").asText()).isEqualTo("rejected");
    assertThat(result.path("results").get(1).path("errors").path("orgId").asText()).isNotBlank();
    assertThat(result.path("results").get(2).path("message").asText()).contains("locked");
    // Site 2 is outside the collector's organisations.
    assertThat(result.path("results").get(3).path("outcome").asText()).isEqualTo("rejected");

    // Resending a refused operation gives the same answer (nothing changed on the device).
    JsonNode again = sync(collector(), "phone-v", List.of(op(UUID.fromString(result.path("results").get(1).path("opId").asText()),
        UUID.fromString(result.path("results").get(1).path("clientId").asText()), Instant.now(), null, null, bad)));
    assertThat(again.path("results").get(0).path("replayed").asBoolean()).isTrue();
    assertThat(again.path("results").get(0).path("errors").path("orgId").asText()).isNotBlank();
  }

  @Test
  void requestLimitsAndPermissions() throws Exception {
    List<String> tooMany = new ArrayList<>();
    for (int i = 0; i < OfflineDtos.MAX_OPERATIONS + 1; i++) {
      tooMany.add(op(point(ORG_SITE_1, "petrol_litres", "2026-01-01", 1), Instant.now()));
    }
    send(collector(), "phone", tooMany).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.operations").value("between 1 and 500 operations per request"));
    send(collector(), "", List.of(op(point(ORG_SITE_1, "petrol_litres", nextDay(), 1), Instant.now()))).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.deviceId").exists());
    send(bearerForRole("AUDITOR"), "phone", List.of(op(point(ORG_SITE_1, "petrol_litres", nextDay(), 1), Instant.now())))
        .andExpect(status().isForbidden());
  }

  @Test
  void incidentsReportedOfflineAreRecordedOnce() throws Exception {
    UUID client = UUID.randomUUID();
    String incident = "{\"orgId\":\"" + ORG_SITE_1 + "\",\"title\":\"Slip near the generator house\",\"incidentType\":\"near_miss\","
        + "\"outcome\":\"none\",\"occurredAt\":\"2026-09-30T08:15\",\"location\":\"Generator house\"}";
    String body = "{\"opId\":\"%s\",\"clientId\":\"" + client + "\",\"kind\":\"safety_incident\",\"capturedAt\":\"" + Instant.now()
        + "\",\"incident\":" + incident + "}";
    String manager = bearerForRole("ESG_MANAGER");
    JsonNode first = sync(manager, "phone-hse", List.of(body.formatted(UUID.randomUUID()))).path("results").get(0);
    assertThat(first.path("outcome").asText()).isEqualTo("applied");
    assertThat(first.path("message").asText()).isEqualTo("Reported");
    // A second send of the same report (a new operation) is the same incident.
    JsonNode second = sync(manager, "phone-hse", List.of(body.formatted(UUID.randomUUID()))).path("results").get(0);
    assertThat(second.path("recordId").asText()).isEqualTo(first.path("recordId").asText());
    assertThat(second.path("message").asText()).isEqualTo("Already reported");
    assertThat(count("select count(*) from social.safety_incidents where id = '" + first.path("recordId").asText() + "'")).isEqualTo(1);
    // Someone without social:create can't report one this way either.
    JsonNode refused = sync(collector(), "phone-c", List.of(body.formatted(UUID.randomUUID()).replace(client.toString(), UUID.randomUUID().toString())))
        .path("results").get(0);
    assertThat(refused.path("outcome").asText()).isEqualTo("rejected");
    assertThat(refused.path("message").asText()).isEqualTo("You can't report incidents");
  }

  /** TRD §11.2.4-05: a device that has been offline for a long time catches up with 500 records in under 30 seconds. */
  @Test
  void fiveHundredRecordsSyncInUnderThirtySeconds() throws Exception {
    List<String> ops = new ArrayList<>();
    LocalDate start = LocalDate.of(2025, 1, 1);
    for (int i = 0; i < 500; i++) {
      ops.add(op(point(ORG_SITE_2, "natural_gas_m3", start.plusDays(i).toString(), 1000 + i), Instant.now()));
    }
    String director = bearerForRole("ESG_DIRECTOR");
    long began = System.nanoTime();
    JsonNode result = sync(director, "phone-500", ops);
    double seconds = (System.nanoTime() - began) / 1e9;
    assertThat(result.path("applied").asInt()).isEqualTo(500);
    assertThat(seconds).as("500 records took %.1f s", seconds).isLessThan(30);
    System.out.printf("WP-43 offline sync: 500 records in %.1f s%n", seconds);
  }
}
