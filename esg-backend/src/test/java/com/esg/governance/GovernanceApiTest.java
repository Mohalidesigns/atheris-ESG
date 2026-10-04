package com.esg.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** WP-34 M8 Governance & Ethics through the API: board and code checks, policy lifecycle, whistleblower channel. */
class GovernanceApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;

  private static final int YEAR = LocalDate.now(ZoneId.of("Africa/Lagos")).getYear();

  private JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  private ResultActions send(MockHttpServletRequestBuilder b, String token, String body) throws Exception {
    if (token != null) {
      b = b.header("Authorization", token);
    }
    return mvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private String member(String name, String role, boolean independent, boolean executive, String gender, String appointed, String committees)
      throws Exception {
    return data(send(post("/v1/governance/board-members"), bearerForRole("ESG_DIRECTOR"), "{\"name\":\"" + name + "\",\"boardRole\":\"" + role
        + "\",\"independent\":" + independent + ",\"executive\":" + executive + ",\"gender\":\"" + gender + "\",\"ageBand\":\"50_59\","
        + "\"appointedOn\":\"" + appointed + "\",\"committees\":[" + committees + "],\"skills\":[\"Finance\",\"ESG\"]}")
        .andExpect(status().isCreated())).path("id").asText();
  }

  private static Map<String, JsonNode> byCode(JsonNode results) {
    Map<String, JsonNode> out = new HashMap<>();
    results.forEach(r -> out.put(r.path("code").asText(), r));
    return out;
  }

  // ---------------------------------------------------------------- board and code checks (journey 21)

  @Test
  void boardCompositionFeedsTheGovernanceCodeChecker() throws Exception {
    // A known board: other tests (field round trips) also add members to tenant A.
    asOwnerInTenantA("update governance.board_members set deleted_at = now() where deleted_at is null");
    String director = bearerForRole("ESG_DIRECTOR");
    // Validation mirrors the board model.
    send(post("/v1/governance/board-members"), director, "{\"name\":\"X\",\"boardRole\":\"independent_non_executive_director\","
        + "\"independent\":false,\"executive\":false,\"appointedOn\":\"2020-01-01\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.boardRole").exists());
    send(post("/v1/governance/board-members"), director, "{\"name\":\"X\",\"boardRole\":\"md_ceo\",\"independent\":true,\"executive\":true,"
        + "\"appointedOn\":\"2020-01-01\",\"leftOn\":\"2019-01-01\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.independent").exists()).andExpect(jsonPath("$.errors.leftOn").exists());
    send(post("/v1/governance/board-members"), director, "{\"name\":\"\",\"boardRole\":\"emperor\",\"appointedOn\":null}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists()).andExpect(jsonPath("$.errors.boardRole").exists());
    send(post("/v1/governance/board-members"), bearerForRole("ESG_MANAGER"), "{\"name\":\"X\",\"boardRole\":\"chair\",\"independent\":false,"
        + "\"executive\":false,\"appointedOn\":\"2020-01-01\"}").andExpect(status().isForbidden());

    member("Ngozi Chair", "chair", false, false, "female", "2019-05-01", "\"nomination_governance\"");
    member("Tunde MD", "md_ceo", false, true, "male", "2021-01-01", "");
    member("Amaka Okoro", "independent_non_executive_director", true, false, "female", "2012-03-01", "\"audit\",\"risk\"");
    member("Bello Musa", "independent_non_executive_director", true, false, "male", "2022-06-01", "\"audit\"");
    String third = member("Chika Eze", "independent_non_executive_director", true, false, "female", "2023-02-01", "\"audit\"");
    member("Sola Sec", "company_secretary", false, false, "male", "2020-01-01", "");
    member("Former Director", "non_executive_director", false, false, "male", "2010-01-01", "");
    String former = data(mvc.perform(get("/v1/governance/board-members?includeFormer=true").header("Authorization", director)))
        .findValues("name").stream().map(JsonNode::asText).filter(n -> n.equals("Former Director")).findFirst().orElseThrow();
    assertThat(former).isNotNull();

    JsonNode comp = data(mvc.perform(get("/v1/governance/board-composition").header("Authorization", bearerForRole("BOARD_MEMBER")))
        .andExpect(status().isOk()));
    assertThat(comp.path("directors").asInt()).isEqualTo(6); // the company secretary isn't a director
    assertThat(comp.path("independent").asInt()).isEqualTo(3);
    assertThat(comp.path("independentPct").decimalValue()).isEqualByComparingTo("50.0");
    assertThat(comp.path("female").asInt()).isEqualTo(3);
    assertThat(comp.path("companySecretary").asBoolean()).isTrue();
    assertThat(comp.path("committees").path("audit").asInt()).isEqualTo(3);
    assertThat(comp.path("skills").path("ESG").asInt()).isEqualTo(6);

    Map<String, JsonNode> checks = byCode(data(mvc.perform(get("/v1/governance/code-check").header("Authorization", director))).path("results"));
    assertThat(checks.get("CAMA-275").path("result").asText()).isEqualTo("pass");   // 3 independent directors
    assertThat(checks.get("CAMA-265").path("result").asText()).isEqualTo("pass");   // chair ≠ MD/CEO
    assertThat(checks.get("CAMA-330").path("result").asText()).isEqualTo("pass");
    assertThat(checks.get("CAMA-404").path("result").asText()).isEqualTo("pass");
    assertThat(checks.get("NCCG-2").path("result").asText()).isEqualTo("pass");     // 5 of 6 non-executive
    assertThat(checks.get("NCCG-7").path("result").asText()).isEqualTo("fail");     // Amaka Okoro appointed 2012
    assertThat(checks.get("NCCG-7").path("evidence").asText()).contains("Amaka Okoro");
    assertThat(checks.get("NCCG-11-REM").path("result").asText()).isEqualTo("fail");
    assertThat(checks.get("KING-7-CHAIR").path("result").asText()).isEqualTo("fail"); // chair isn't independent
    assertThat(checks.get("NCCG-7").path("verificationStatus").asText()).isEqualTo("pending_sme");
    JsonNode cama = data(mvc.perform(get("/v1/governance/code-check?ruleSet=CAMA_2020").header("Authorization", director)));
    assertThat(cama.path("results")).hasSize(5);
    mvc.perform(get("/v1/governance/code-check?ruleSet=SOX").header("Authorization", director)).andExpect(status().isBadRequest());

    // One independent director leaves: CAMA s.275 now fails.
    JsonNode m = data(mvc.perform(get("/v1/governance/board-members/" + third).header("Authorization", director)));
    send(put("/v1/governance/board-members/" + third), director, "{\"name\":\"Chika Eze\",\"boardRole\":\"independent_non_executive_director\","
        + "\"independent\":true,\"executive\":false,\"gender\":\"female\",\"appointedOn\":\"2023-02-01\",\"leftOn\":\"" + LocalDate.now().minusDays(1)
        + "\",\"committees\":[\"audit\"],\"version\":" + m.path("version").asLong() + "}").andExpect(status().isOk());
    checks = byCode(data(mvc.perform(get("/v1/governance/code-check").header("Authorization", director))).path("results"));
    assertThat(checks.get("CAMA-275").path("result").asText()).isEqualTo("fail");
    assertThat(checks.get("CAMA-275").path("evidence").asText()).contains("2 independent");

    // The register evidences GRI 2-9 in the gap analysis (narrative: partly met until attested).
    JsonNode gri = data(mvc.perform(get("/v1/compliance/gaps?framework=GRI&year=" + YEAR).header("Authorization", bearerForRole("COMPLIANCE_OFFICER"))));
    JsonNode r29 = null;
    for (JsonNode r : gri.path("requirements")) {
      if ("2-9".equals(r.path("code").asText())) {
        r29 = r;
      }
    }
    assertThat(r29.path("status").asText()).isEqualTo("partial");

    // The dashboard shows board health to governance readers.
    JsonNode dash = data(mvc.perform(get("/v1/dashboard/executive").header("Authorization", director)));
    assertThat(dash.path("governance").path("directors").asInt()).isEqualTo(5);
    assertThat(dash.path("governance").path("codeChecksFailed").asInt()).isPositive();
    assertThat(dash.path("governance").has("openCases")).isFalse(); // the director can't see whistleblower counts
  }

  // ---------------------------------------------------------------- policy lifecycle (journey 20)

  @Test
  void aPolicyIsApprovedBySomeoneElsePublishedAndAcknowledgedPerVersion() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    String board = bearerForRole("BOARD_MEMBER");
    String collector = bearerForRole("DATA_COLLECTOR");
    String body = "{\"name\":\"Anti-bribery policy %s\",\"category\":\"anti_bribery_corruption\",\"policyVersion\":\"%s\",\"summary\":\"No bribes\","
        + "\"content\":\"We do not offer or accept bribes.\",\"ownerUserId\":\"" + roleUserId("COMPLIANCE_OFFICER") + "\",\"reviewMonths\":12,"
        + "\"requiresAcknowledgement\":true%s}";
    String tag = UUID.randomUUID().toString().substring(0, 6);
    send(post("/v1/governance/policies"), director, "{\"name\":\"\",\"category\":\"x\",\"policyVersion\":\"1 0\",\"ownerUserId\":null,"
        + "\"reviewMonths\":0,\"requiresAcknowledgement\":true}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.name").exists()).andExpect(jsonPath("$.errors.category").exists())
        .andExpect(jsonPath("$.errors.policyVersion").exists()).andExpect(jsonPath("$.errors.reviewMonths").exists());
    send(post("/v1/governance/policies"), director, String.format(body, tag, "1.0", "").replace("\"content\":\"We do not offer or accept bribes.\",", ""))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.content").exists());
    JsonNode p = data(send(post("/v1/governance/policies"), director, String.format(body, tag, "1.0", "")).andExpect(status().isCreated()));
    String id = p.path("id").asText();
    assertThat(p.path("status").asText()).isEqualTo("draft");
    assertThat(p.path("actions").toString()).contains("edit", "submit");

    p = data(send(put("/v1/governance/policies/" + id + "/submit"), director, "{\"version\":" + p.path("version").asLong() + "}"));
    assertThat(p.path("status").asText()).isEqualTo("in_review");
    // Segregation of duties: the submitter can't approve (shown as blocked, refused by the server, audited).
    assertThat(p.path("blockedActions").path("approve").asText()).contains("someone else must approve");
    send(put("/v1/governance/policies/" + id + "/approve"), director, "{\"version\":" + p.path("version").asLong() + "}")
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-SOD"));
    p = data(send(put("/v1/governance/policies/" + id + "/approve"), board, "{\"version\":" + p.path("version").asLong() + "}")
        .andExpect(status().isOk()));
    assertThat(p.path("approvedBy").asText()).isEqualTo("Board Member Fixture");
    p = data(send(put("/v1/governance/policies/" + id + "/publish"), director, "{\"version\":" + p.path("version").asLong() + "}"));
    assertThat(p.path("status").asText()).isEqualTo("published");
    assertThat(p.path("nextReview").asText()).isEqualTo(LocalDate.now(ZoneId.of("Africa/Lagos")).plusMonths(12).toString());

    // Everyone is asked to acknowledge it; a collector (no governance access) can.
    JsonNode mine = data(mvc.perform(get("/v1/governance/my-policies").header("Authorization", collector)).andExpect(status().isOk()));
    assertThat(findPolicy(mine, id).path("acknowledgedAt").isNull() || findPolicy(mine, id).path("acknowledgedAt").isMissingNode()).isTrue();
    mvc.perform(post("/v1/governance/my-policies/" + id + "/acknowledge").header("Authorization", collector)).andExpect(status().isOk());
    mvc.perform(post("/v1/governance/my-policies/" + id + "/acknowledge").header("Authorization", collector)).andExpect(status().isOk()); // idempotent
    p = data(mvc.perform(get("/v1/governance/policies/" + id).header("Authorization", director)));
    assertThat(p.path("acknowledged").asInt()).isEqualTo(1);
    assertThat(inbox(roleUserId("DATA_COLLECTOR"), "POLICY_PUBLISHED")).isPositive();

    // A revision needs a reason and a new version label; colleagues acknowledge the new version again.
    send(put("/v1/governance/policies/" + id + "/revise"), director, "{\"version\":" + p.path("version").asLong() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
    p = data(send(put("/v1/governance/policies/" + id + "/revise"), director, "{\"version\":" + p.path("version").asLong() + ",\"reason\":\"New law\"}"));
    p = data(send(put("/v1/governance/policies/" + id + "/submit"), director, "{\"version\":" + p.path("version").asLong() + "}"));
    p = data(send(put("/v1/governance/policies/" + id + "/approve"), board, "{\"version\":" + p.path("version").asLong() + "}"));
    send(put("/v1/governance/policies/" + id + "/publish"), director, "{\"version\":" + p.path("version").asLong() + "}")
        .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("new version label")));
    p = data(send(put("/v1/governance/policies/" + id + "/revise"), director, "{\"version\":" + p.path("version").asLong() + ",\"reason\":\"Relabel\"}"));
    p = data(send(put("/v1/governance/policies/" + id), director, String.format(body, tag, "2.0", ",\"version\":" + p.path("version").asLong())));
    p = data(send(put("/v1/governance/policies/" + id + "/submit"), director, "{\"version\":" + p.path("version").asLong() + "}"));
    p = data(send(put("/v1/governance/policies/" + id + "/approve"), board, "{\"version\":" + p.path("version").asLong() + "}"));
    p = data(send(put("/v1/governance/policies/" + id + "/publish"), director, "{\"version\":" + p.path("version").asLong() + "}").andExpect(status().isOk()));
    mine = data(mvc.perform(get("/v1/governance/my-policies").header("Authorization", collector)));
    assertThat(findPolicy(mine, id).path("policyVersion").asText()).isEqualTo("2.0");
    assertThat(findPolicy(mine, id).path("acknowledgedAt").isNull() || findPolicy(mine, id).path("acknowledgedAt").isMissingNode()).isTrue();

    // Editing a published policy is refused; retiring needs a reason.
    send(put("/v1/governance/policies/" + id), director, String.format(body, tag, "2.1", ",\"version\":" + p.path("version").asLong()))
        .andExpect(status().isConflict());
    send(put("/v1/governance/policies/" + id + "/retire"), board, "{\"version\":" + p.path("version").asLong() + ",\"reason\":\"Superseded\"}")
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("retired"));
    send(put("/v1/governance/policies/" + id + "/explode"), director, "{\"version\":1}").andExpect(status().isNotFound());
  }

  private static JsonNode findPolicy(JsonNode list, String id) {
    for (JsonNode p : list) {
      if (id.equals(p.path("id").asText())) {
        return p;
      }
    }
    throw new AssertionError("policy " + id + " not in " + list);
  }

  // ---------------------------------------------------------------- whistleblower (journey 19)

  @Test
  void anAnonymousReportIsReceivedEncryptedInvestigatedAndFollowedByTheReporter() throws Exception {
    String description = "The procurement manager asked a supplier for a cash payment to win the tender.";
    // No sign-in at all.
    JsonNode receipt = data(send(post("/v1/public/whistleblower/tenant-a"), null, "{\"category\":\"bribery_corruption\",\"description\":\""
        + description + "\"}").andExpect(status().isCreated()));
    String ref = receipt.path("caseReference").asText();
    String secret = receipt.path("secret").asText();
    assertThat(ref).matches("WB-" + YEAR + "-\\d{4}");
    assertThat(secret).matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}");
    send(post("/v1/public/whistleblower/tenant-a"), null, "{\"category\":\"gossip\",\"description\":\"short\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.category").exists()).andExpect(jsonPath("$.errors.description").exists());
    send(post("/v1/public/whistleblower/no-such-tenant"), null, "{\"category\":\"fraud\",\"description\":\"" + description + "\"}")
        .andExpect(status().isNotFound());

    // Stored encrypted, with no user or client recorded anywhere.
    try (Connection c = PostgresTestDatabase.ownerConnection(); Statement st = c.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      try (ResultSet rs = st.executeQuery("select id, description from governance.whistleblower_reports where case_reference = '" + ref + "'")) {
        rs.next();
        assertThat(rs.getString(2)).doesNotContain("procurement").isNotBlank();
        try (Statement st2 = c.createStatement(); ResultSet a = st2.executeQuery("select user_id, ip_address, user_agent from audit.audit_logs "
            + "where resource = 'governance.whistleblower_reports' and resource_id = '" + rs.getString(1) + "' and action = 'insert'")) {
          assertThat(a.next()).isTrue();
          assertThat(a.getObject(1)).isNull();
          assertThat(a.getObject(2)).isNull();
          assertThat(a.getObject(3)).isNull();
        }
      }
    }
    // Investigators (and only they) are told, without any content.
    assertThat(inbox(roleUserId("ETHICS_OFFICER"), "WHISTLEBLOWER_CASE_RECEIVED")).isPositive();
    assertThat(inbox(roleUserId("TENANT_ADMIN"), "WHISTLEBLOWER_CASE_RECEIVED")).isZero();

    // The reporter follows the case with the reference and secret; a wrong secret looks like an unknown case.
    send(post("/v1/public/whistleblower/tenant-a/status"), null, "{\"caseReference\":\"" + ref + "\",\"secret\":\"" + secret + "\"}")
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("received"));
    send(post("/v1/public/whistleblower/tenant-a/status"), null, "{\"caseReference\":\"" + ref + "\",\"secret\":\"AAAA-AAAA-AAAA-AAAA\"}")
        .andExpect(status().isNotFound());

    // Only investigators can open cases: not the tenant admin, not the director (and the database agrees).
    mvc.perform(get("/v1/governance/whistleblower/cases").header("Authorization", bearerForRole("TENANT_ADMIN"))).andExpect(status().isForbidden());
    mvc.perform(get("/v1/governance/whistleblower/cases").header("Authorization", bearerForRole("ESG_DIRECTOR"))).andExpect(status().isForbidden());
    try (Connection app = PostgresTestDatabase.appConnection(); Statement st = app.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false), "
          + "set_config('app.current_user', '" + roleUserId("TENANT_ADMIN") + "', false)");
      try (ResultSet rs = st.executeQuery("select count(*) from governance.whistleblower_reports")) {
        rs.next();
        assertThat(rs.getInt(1)).isZero();
      }
      assertThat(st.executeUpdate("update governance.whistleblower_reports set status = 'closed'")).isZero(); // invisible, so untouched
    }

    String officer = bearerForRole("ETHICS_OFFICER");
    JsonNode list = data(mvc.perform(get("/v1/governance/whistleblower/cases").header("Authorization", officer)).andExpect(status().isOk()));
    String id = null;
    for (JsonNode c : list) {
      if (ref.equals(c.path("caseReference").asText())) {
        id = c.path("id").asText();
        assertThat(c.has("description")).isFalse(); // the list never carries content
      }
    }
    JsonNode c = data(mvc.perform(get("/v1/governance/whistleblower/cases/" + id).header("Authorization", officer)).andExpect(status().isOk()));
    assertThat(c.path("description").asText()).isEqualTo(description);
    // Reading a case is audited.
    mvc.perform(get("/v1/audit/logs?action=viewed&resourceId=" + id).header("Authorization", bearerForRole("TENANT_ADMIN")))
        .andExpect(jsonPath("$.data.content[0].userId").value(roleUserId("ETHICS_OFFICER")));

    // Workflow: no jumping to resolved; an outcome is needed to resolve; the reporter sees only the public update.
    send(put("/v1/governance/whistleblower/cases/" + id), officer, "{\"version\":" + c.path("version").asLong() + ",\"status\":\"resolved\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.status").exists()).andExpect(jsonPath("$.errors.resolution").exists());
    c = data(send(put("/v1/governance/whistleblower/cases/" + id), officer, "{\"version\":" + c.path("version").asLong() + ",\"status\":\"triage\","
        + "\"assignedTo\":\"" + roleUserId("ETHICS_OFFICER") + "\",\"publicUpdate\":\"We are looking into your report.\"}").andExpect(status().isOk()));
    send(put("/v1/governance/whistleblower/cases/" + id), officer, "{\"version\":" + c.path("version").asLong() + ",\"status\":\"triage\","
        + "\"assignedTo\":\"" + roleUserId("ESG_DIRECTOR") + "\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.assignedTo").exists());
    c = data(send(post("/v1/governance/whistleblower/cases/" + id + "/notes"), officer, "{\"note\":\"Interviewed the tender committee.\"}"));
    assertThat(c.path("notes").get(0).path("text").asText()).isEqualTo("Interviewed the tender committee.");
    c = data(send(put("/v1/governance/whistleblower/cases/" + id), officer, "{\"version\":" + c.path("version").asLong()
        + ",\"status\":\"investigating\",\"assignedTo\":\"" + roleUserId("ETHICS_OFFICER") + "\",\"publicUpdate\":\"An investigation is under way.\"}"));
    c = data(send(put("/v1/governance/whistleblower/cases/" + id), officer, "{\"version\":" + c.path("version").asLong()
        + ",\"status\":\"resolved\",\"assignedTo\":\"" + roleUserId("ETHICS_OFFICER") + "\",\"publicUpdate\":\"Action has been taken. Thank you.\","
        + "\"resolution\":\"Manager suspended; tender re-run.\"}").andExpect(status().isOk()));
    assertThat(c.path("resolution").asText()).isEqualTo("Manager suspended; tender re-run.");
    send(post("/v1/public/whistleblower/tenant-a/status"), null, "{\"caseReference\":\"" + ref.toLowerCase() + "\",\"secret\":\"" + secret.toLowerCase() + "\"}")
        .andExpect(jsonPath("$.data.status").value("resolved"))
        .andExpect(jsonPath("$.data.publicUpdate").value("Action has been taken. Thank you."));

    // The board sees counts only; others nothing.
    JsonNode stats = data(mvc.perform(get("/v1/governance/whistleblower/statistics").header("Authorization", bearerForRole("BOARD_MEMBER")))
        .andExpect(status().isOk()));
    assertThat(stats.path("total").asInt()).isPositive();
    assertThat(stats.path("byCategory").path("bribery_corruption").asInt()).isPositive();
    mvc.perform(get("/v1/governance/whistleblower/statistics").header("Authorization", bearerForRole("ESG_ANALYST"))).andExpect(status().isForbidden());
    mvc.perform(get("/v1/governance/whistleblower/cases/" + id).header("Authorization", bearerForRole("BOARD_MEMBER"))).andExpect(status().isForbidden());
  }

  @Test
  void governanceUsersCanShareTheirPublicReportingLink() throws Exception {
    mvc.perform(get("/v1/governance/whistleblower/channel").header("Authorization", bearerForRole("BOARD_MEMBER")))
        .andExpect(jsonPath("$.data.path").value("/whistleblower/tenant-a"));
  }

  @Test
  void theAnonymousChannelIsThrottled() throws Exception {
    int status = 0;
    for (int i = 0; i < IntakeRateLimiter.SUBMISSIONS + 2 && status != 429; i++) {
      status = send(post("/v1/public/whistleblower/tenant-b"), null, "{\"category\":\"other\",\"description\":\"Throttle check number " + i
          + " with enough text.\"}").andReturn().getResponse().getStatus();
    }
    assertThat(status).isEqualTo(429);
  }

  @Test
  void reportsCanQuoteGovernanceFigures() throws Exception {
    String manager = bearerForRole("ESG_MANAGER");
    JsonNode templates = data(mvc.perform(get("/v1/reports/templates").header("Authorization", manager)));
    String template = null;
    for (JsonNode t : templates) {
      if ("gri_core".equals(t.path("key").asText())) {
        template = t.path("id").asText();
      }
    }
    JsonNode r = data(send(post("/v1/reports"), manager, "{\"templateId\":\"" + template + "\",\"title\":\"Governance figures\","
        + "\"reportingPeriodStart\":\"" + YEAR + "-01-01\",\"reportingPeriodEnd\":\"" + YEAR + "-12-31\"}").andExpect(status().isCreated()));
    JsonNode saved = data(send(put("/v1/reports/" + r.path("id").asText() + "/sections"), manager, "{\"version\":" + r.path("version").asLong()
        + ",\"sections\":[{\"title\":\"Governance\",\"type\":\"narrative\",\"content\":\"Board: {{governance:board_size}} directors, "
        + "{{governance:board_independent_pct}} independent. Cases: {{governance:whistleblower_cases}}.\"}]}").andExpect(status().isOk()));
    assertThat(saved.path("snapshot").has("governance:board_size")).isTrue();
    assertThat(saved.path("snapshot").path("governance:whistleblower_cases").path("status").asText()).isEqualTo("ok");
    send(put("/v1/reports/" + r.path("id").asText() + "/sections"), manager, "{\"version\":" + saved.path("version").asLong()
        + ",\"sections\":[{\"title\":\"X\",\"type\":\"narrative\",\"content\":\"{{governance:ceo_salary}}\"}]}").andExpect(status().isBadRequest());
  }

  private static int inbox(String userId, String type) throws Exception {
    try (Connection c = PostgresTestDatabase.ownerConnection(); Statement st = c.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      try (ResultSet rs = st.executeQuery("select count(*) from notifications.notification_queue where user_id = '" + userId
          + "' and notification_type = '" + type + "'")) {
        rs.next();
        return rs.getInt(1);
      }
    }
  }

  @SuppressWarnings("unused")
  private static List<String> names(JsonNode list) {
    return list.findValues("name").stream().map(JsonNode::asText).toList();
  }
}
