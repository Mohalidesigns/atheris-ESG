package com.esg.carbonmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * WP-41 M11 Carbon Market through the API: journey 24 (credits recorded → retired → a serial can't be retired twice, in
 * this or any other tenant), the period lock and segregation of duties on retirements, internal pricing, offset
 * monitoring, the Sandbox registry, links and access.
 */
class CarbonMarketApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;

  private static final int YEAR = LocalDate.now(ZoneId.of("Africa/Lagos")).getYear();

  private JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  private ResultActions send(MockHttpServletRequestBuilder b, String token, String body) throws Exception {
    return mvc.perform(b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode read(String path, String token) throws Exception {
    return data(mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk()));
  }

  private static String project(String tag) {
    return "{\"name\":\"Ogun cookstoves " + tag + "\",\"standard\":\"verra_vcs\",\"registry\":\"Verra Registry\",\"registryProjectId\":\"VCS-" + tag + "\","
        + "\"projectType\":\"clean_cookstoves\",\"countryCode\":\"NG\",\"methodology\":\"VMR0006\",\"status\":\"active\"}";
  }

  private static String lot(String projectId, String prefix, long from, long to, String extra) {
    return "{\"projectId\":\"" + projectId + "\",\"vintage\":2023,\"serialPrefix\":\"" + prefix + "\",\"serialFrom\":" + from + ",\"serialTo\":" + to
        + ",\"acquiredOn\":\"2025-03-01\",\"pricePerTonne\":12.5,\"currency\":\"USD\",\"seller\":\"Broker Ltd\"" + extra + "}";
  }

  private static String retire(String lotId, long quantity, int year) {
    return "{\"lotId\":\"" + lotId + "\",\"quantity\":" + quantity + ",\"purpose\":\"scope_1\",\"claimedYear\":" + year
        + ",\"beneficiary\":\"Atheris Demo plc\",\"note\":\"Diesel generators\"}";
  }

  private ResultActions act(String token, JsonNode r, String body) throws Exception {
    return send(post("/v1/carbon-market/retirements/" + r.path("id").asText() + "/actions"), token, body.replace("}", ",\"version\":"
        + r.path("version").asLong() + "}"));
  }

  @Test
  void journey24CreditsAreRecordedRetiredAndASerialCannotBeRetiredTwice() throws Exception {
    String manager = bearerForRole("ESG_MANAGER");
    String director = bearerForRole("ESG_DIRECTOR");
    String tag = Long.toString(System.nanoTime(), 36).toUpperCase();
    String prefix = "VCS-" + tag + "-2023";

    // The project the credits come from (entered by the tenant; nothing is seeded).
    JsonNode p = data(send(post("/v1/carbon-market/projects"), manager, project(tag)).andExpect(status().isCreated()));
    assertThat(p.path("natureBased").asBoolean()).isFalse();
    send(post("/v1/carbon-market/projects"), manager, project(tag)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.registryProjectId").exists());
    String pid = p.path("id").asText();

    // A lot of 1,000 credits (serials 1001–2000). Serials in order; a price needs its currency.
    send(post("/v1/carbon-market/credits"), manager, lot(pid, prefix, 2000, 1001, "")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.serialTo").exists());
    send(post("/v1/carbon-market/credits"), manager, lot(pid, prefix, 1001, 2000, "").replace(",\"currency\":\"USD\"", "")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.currency").exists());
    JsonNode l = data(send(post("/v1/carbon-market/credits"), manager, lot(pid, prefix, 1001, 2000, "")).andExpect(status().isCreated()));
    String lotId = l.path("id").asText();
    assertThat(l.path("quantity").asLong()).isEqualTo(1000);
    assertThat(l.path("available").asLong()).isEqualTo(1000);
    assertThat(l.path("verificationStatus").asText()).isEqualTo("unverified");
    // The same serials can't be held twice (case doesn't matter in the batch).
    send(post("/v1/carbon-market/credits"), manager, lot(pid, prefix.toLowerCase(), 1500, 2500, "")).andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ESG-409-SERIAL"));
    // The registry is a Sandbox: it says it checked nothing.
    JsonNode check = read("/v1/carbon-market/credits/" + lotId + "/registry-check", manager);
    assertThat(check.path("sandbox").asBoolean()).isTrue();
    assertThat(check.path("status").asText()).isEqualTo("not_connected");

    // Verified by an approver from the attached registry statement.
    send(post("/v1/carbon-market/credits/" + lotId + "/verify"), manager, "{\"action\":\"verify\",\"version\":" + l.path("version").asLong() + "}")
        .andExpect(status().isForbidden());
    send(post("/v1/carbon-market/credits/" + lotId + "/verify"), director, "{\"action\":\"verify\",\"version\":" + l.path("version").asLong() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.fileId").exists());
    String statement = data(mvc.perform(multipart("/v1/files").file(new MockMultipartFile("file", "verra-statement.pdf", "application/pdf",
        "%PDF-1.4 statement".getBytes(StandardCharsets.US_ASCII))).header("Authorization", director)).andExpect(status().isCreated())).path("id").asText();
    l = data(send(post("/v1/carbon-market/credits/" + lotId + "/verify"), director, "{\"action\":\"verify\",\"fileId\":\"" + statement + "\",\"version\":"
        + l.path("version").asLong() + "}").andExpect(status().isOk()));
    assertThat(l.path("verificationStatus").asText()).isEqualTo("verified");

    // Retire 300 against this year's Scope 1: serials 1001–1300 are reserved; 800 more don't fit.
    JsonNode r = data(send(post("/v1/carbon-market/retire"), manager, retire(lotId, 300, YEAR)).andExpect(status().isCreated()));
    assertThat(r.path("serialFrom").asLong()).isEqualTo(1001);
    assertThat(r.path("serialTo").asLong()).isEqualTo(1300);
    assertThat(r.path("status").asText()).isEqualTo("requested");
    send(post("/v1/carbon-market/retire"), manager, retire(lotId, 800, YEAR)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.quantity").value("only 700 credits of this lot are still available"));
    send(post("/v1/carbon-market/retire"), manager, retire(lotId, 10, YEAR + 1)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.claimedYear").exists());

    // Approval: carbon_market:approve, the registry reference and date; the manager can't.
    act(manager, r, "{\"action\":\"approve\",\"registryReference\":\"R-1\",\"retiredOn\":\"" + LocalDate.now() + "\"}").andExpect(status().isForbidden());
    act(director, r, "{\"action\":\"approve\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.registryReference").exists())
        .andExpect(jsonPath("$.errors.retiredOn").exists());
    r = data(act(director, r, "{\"action\":\"approve\",\"registryReference\":\"VCU-RET-" + tag + "\",\"retiredOn\":\"" + LocalDate.now(ZoneId.of("Africa/Lagos"))
        + "\",\"fileId\":\"" + statement + "\"}").andExpect(status().isOk()));
    assertThat(r.path("status").asText()).isEqualTo("retired");
    assertThat(r.path("approvedByName").asText()).isEqualTo("Esg Director Fixture");

    // Whoever requests can't approve.
    JsonNode own = data(send(post("/v1/carbon-market/retire"), director, retire(lotId, 100, YEAR)).andExpect(status().isCreated()));
    assertThat(own.path("serialFrom").asLong()).isEqualTo(1301);
    assertThat(own.path("blockedActions").path("approve").asText()).contains("someone else");
    act(director, own, "{\"action\":\"approve\",\"registryReference\":\"X\",\"retiredOn\":\"" + LocalDate.now() + "\"}").andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ESG-409-SOD"));
    // Cancelling releases the serials: the next request takes 1301 again.
    act(director, own, "{\"action\":\"cancel\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.note").exists());
    act(director, own, "{\"action\":\"cancel\",\"note\":\"Wrong year\"}").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("cancelled"));
    JsonNode again = data(send(post("/v1/carbon-market/retire"), manager, retire(lotId, 100, YEAR)).andExpect(status().isCreated()));
    assertThat(again.path("serialFrom").asLong()).isEqualTo(1301);

    // A locked year (FY2020 in the fixture) can't be offset.
    send(post("/v1/carbon-market/retire"), manager, retire(lotId, 10, 2020)).andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ESG-409-LOCKED"));

    // No double counting, even past the API: the database refuses a second retirement of serial 1001 — and so does
    // another tenant trying to hold or retire the same serials.
    String key = "verra_vcs:" + prefix;
    assertThatThrownBy(() -> owner(PostgresTestDatabase.TENANT_A, "insert into carbon_market.retirements (lot_id, serial_key, serial_from, serial_to, purpose, "
        + "claimed_year, beneficiary) values ('" + lotId + "', '" + key + "', 1001, 1001, 'voluntary', " + YEAR + ", 'Again')"))
        .isInstanceOf(SQLException.class).hasMessageContaining("already retired or reserved");
    String bProject = UUID.randomUUID().toString();
    assertThatThrownBy(() -> owner(PostgresTestDatabase.TENANT_B,
        "insert into carbon_market.offset_projects (id, name, standard, registry, registry_project_id, project_type, country_code) values ('" + bProject
            + "', 'B copy', 'verra_vcs', 'Verra Registry', 'VCS-B-" + tag + "', 'clean_cookstoves', 'KE')",
        "insert into carbon_market.credit_lots (project_id, vintage, serial_prefix, serial_key, serial_from, serial_to, acquired_on) values ('" + bProject
            + "', 2023, '" + prefix + "', '" + key + "', 1200, 1250, '2025-04-01')"))
        .isInstanceOf(SQLException.class).hasMessageContaining("already recorded in another holding").hasMessageNotContaining("Tenant A");

    // The lot now shows 300 retired, 100 reserved, 600 available; serials can't change once used.
    l = read("/v1/carbon-market/credits/" + lotId, manager);
    assertThat(l.path("retired").asLong()).isEqualTo(300);
    assertThat(l.path("reserved").asLong()).isEqualTo(100);
    assertThat(l.path("available").asLong()).isEqualTo(600);
    send(put("/v1/carbon-market/credits/" + lotId), manager, lot(pid, prefix, 1001, 1900, ",\"version\":" + l.path("version").asLong()))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.serialFrom").exists());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/v1/carbon-market/credits/" + lotId).header("Authorization", manager))
        .andExpect(status().isConflict());

    // Retired credits evidence IFRS S2 climate-related targets for the year; the dashboard card shows the holding.
    for (JsonNode req : read("/v1/compliance/gaps?framework=ISSB_S2&year=" + YEAR, bearerForRole("COMPLIANCE_OFFICER")).path("requirements")) {
      if ("S2-33".equals(req.path("code").asText())) {
        assertThat(req.path("status").asText()).isNotEqualTo("missing");
      }
    }
    JsonNode dash = read("/v1/dashboard/executive", director).path("carbonMarket");
    assertThat(dash.path("held").asLong()).isGreaterThanOrEqualTo(1000);
    assertThat(dash.path("registry").path("sandbox").asBoolean()).isTrue();
  }

  @Test
  void offsettingStaysBesideGrossEmissionsAndInternalPricesCostThem() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    // Offsetting: credits beside gross Scope 1+2, consistent with the published formulas (CarbonMarketMathTest).
    JsonNode o = read("/v1/carbon-market/offsetting?year=" + YEAR, director);
    BigDecimal gross12 = o.path("grossScope1").decimalValue().add(o.path("grossScope2").decimalValue());
    BigDecimal credits12 = o.path("creditsScope1").decimalValue().add(o.path("creditsScope2").decimalValue());
    assertThat(o.path("netScope12").decimalValue()).isEqualByComparingTo(gross12.subtract(credits12).max(BigDecimal.ZERO));
    if (gross12.signum() > 0) {
      assertThat(o.path("offsetShare").decimalValue()).isEqualByComparingTo(credits12.multiply(BigDecimal.valueOf(100)).divide(gross12, 1, RoundingMode.HALF_UP));
    }
    assertThat(o.path("ceilingPct").decimalValue()).isPositive();
    assertThat(o.path("message").asText()).isNotBlank();

    // The ceiling is an approver's setting.
    JsonNode s = read("/v1/carbon-market/settings", director);
    send(put("/v1/carbon-market/settings"), bearerForRole("ESG_MANAGER"), "{\"offsetCeilingPct\":5}").andExpect(status().isForbidden());
    send(put("/v1/carbon-market/settings"), director, "{\"offsetCeilingPct\":101,\"version\":" + s.path("version").asLong() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.offsetCeilingPct").exists());

    // An internal shadow price: one per type and start date; the exposure is price × emissions in the scopes covered.
    String date = (2000 + (int) (Math.random() * 20)) + "-0" + (1 + (int) (Math.random() * 9)) + "-01";
    String body = "{\"priceType\":\"shadow\",\"price\":50,\"currency\":\"USD\",\"effectiveFrom\":\"" + date + "\",\"scopes\":[\"scope_1\",\"scope_2\"],"
        + "\"note\":\"Board-approved shadow price\"}";
    send(post("/v1/carbon-market/internal-prices"), bearerForRole("ESG_MANAGER"), body).andExpect(status().isForbidden());
    send(post("/v1/carbon-market/internal-prices"), director, body.replace("[\"scope_1\",\"scope_2\"]", "[]")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.scopes").exists());
    JsonNode price = data(send(post("/v1/carbon-market/internal-prices"), director, body).andExpect(status().isCreated()));
    assertThat(price.path("scopes").toString()).isEqualTo("[\"scope_1\",\"scope_2\"]");
    send(post("/v1/carbon-market/internal-prices"), director, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.effectiveFrom").exists());
    JsonNode e = read("/v1/carbon-market/internal-prices/exposure?year=" + YEAR + "&priceType=shadow", director);
    if (e.path("price").isObject()) {
      BigDecimal p = e.path("price").path("price").decimalValue();
      for (JsonNode row : e.path("organisations")) {
        assertThat(row.path("cost").decimalValue()).isEqualByComparingTo(row.path("tonnes").decimalValue().multiply(p).setScale(2, RoundingMode.HALF_UP));
      }
    }
    mvc.perform(get("/v1/carbon-market/internal-prices/exposure?priceType=levy").header("Authorization", director)).andExpect(status().isBadRequest());

    // The exchange is a Sandbox too: no listings, and it says so.
    JsonNode listings = read("/v1/carbon-market/listings?q=cookstoves", director);
    assertThat(listings.path("sandbox").asBoolean()).isTrue();
    assertThat(listings.path("listings").size()).isZero();
    assertThat(read("/v1/integrations", bearerForRole("TENANT_ADMIN")).toString()).contains("carbon_registry").contains("carbon_exchange");
  }

  @Test
  void accessFollowsTheRoleMatrix() throws Exception {
    for (String role : List.of("ESG_DIRECTOR", "ESG_MANAGER", "AUDITOR")) {
      mvc.perform(get("/v1/carbon-market/credits").header("Authorization", bearerForRole(role))).andExpect(status().isOk());
    }
    for (String role : List.of("DATA_COLLECTOR", "ESG_ANALYST", "INVESTOR_VIEWER", "BOARD_MEMBER")) {
      mvc.perform(get("/v1/carbon-market/credits").header("Authorization", bearerForRole(role))).andExpect(status().isForbidden());
    }
    send(post("/v1/carbon-market/projects"), bearerForRole("AUDITOR"), project("AUD")).andExpect(status().isForbidden());
    mvc.perform(get("/v1/carbon-market/credits").header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B))).andExpect(status().isForbidden());
  }

  private static void owner(String tenant, String... sql) throws SQLException {
    try (Connection c = PostgresTestDatabase.ownerConnection(); Statement st = c.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + tenant + "', false)");
      for (String s : sql) {
        st.execute(s);
      }
    }
  }
}
