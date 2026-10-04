package com.esg.supplychain;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** WP-31 M6 Supply Chain through the API: onboarding, tokenised questionnaires and scoring (journey 15), CAPA, spend → Scope 3. */
class SupplyChainApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;
  @Autowired private CorrectiveActionService actionService;

  private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Lagos"));

  private JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  private ResultActions send(MockHttpServletRequestBuilder b, String token, String body) throws Exception {
    if (token != null) {
      b = b.header("Authorization", token);
    }
    return mvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode read(String path, String token) throws Exception {
    return data(mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk()));
  }

  private static String question(String pillar, String text, String type, String positive, int weight, boolean evidence) {
    return "{\"pillar\":\"" + pillar + "\",\"question\":\"" + text + "\",\"answerType\":\"" + type + "\"" + (positive == null ? "" : ",\"positiveAnswer\":\""
        + positive + "\"") + ",\"weight\":" + weight + ",\"evidenceRequired\":" + evidence + "}";
  }

  /** The golden scorecard of SupplierScoringTest as a template. */
  private static final String QUESTIONS = String.join(",",
      question("E", "Do you measure your energy use?", "yes_no", "yes", 3, false),
      question("E", "How well do you manage waste?", "scale", null, 1, false),
      question("E", "Diesel used last year (litres)", "number", null, 0, false),
      question("S", "Has child labour been found in your operations?", "yes_no", "no", 2, false),
      question("S", "Do you have a health and safety policy?", "yes_no", "yes", 2, true),
      question("G", "How mature is your anti-bribery programme?", "scale", null, 1, false));

  private static String template(String name, int e, int s, int g, String questions) {
    return "{\"name\":\"" + name + "\",\"kind\":\"simplified\",\"weightE\":" + e + ",\"weightS\":" + s + ",\"weightG\":" + g + ",\"questions\":[" + questions + "]}";
  }

  private static String supplier(String name, String onboarding, String email) {
    return "{\"name\":\"" + name + "\",\"tier\":\"tier_1\",\"onboarding\":\"" + onboarding + "\",\"sectorCode\":\"C\",\"countryCode\":\"NG\","
        + "\"stateRegion\":\"Rivers\",\"status\":\"pending\"" + (email == null ? "" : ",\"contactEmail\":\"" + email + "\"") + "}";
  }

  private String publishedTemplate(String director, String name) throws Exception {
    JsonNode t = data(send(post("/v1/supply-chain/templates"), director, template(name, 40, 30, 30, QUESTIONS)).andExpect(status().isCreated()));
    t = data(send(post("/v1/supply-chain/templates/" + t.path("id").asText() + "/actions"), director, "{\"action\":\"publish\",\"version\":"
        + t.path("version").asLong() + "}").andExpect(status().isOk()));
    return t.path("id").asText();
  }

  private static String token(JsonNode sent) {
    String link = sent.path("link").asText();
    return link.substring(link.lastIndexOf('/') + 1);
  }

  // ---------------------------------------------------------------- journey 15

  @Test
  void journey15SupplierOnboardedQuestionnaireAnsweredScoredAndCorrectiveActionClosed() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    String manager = bearerForRole("ESG_MANAGER");
    String tag = Long.toString(System.nanoTime(), 36);

    // The questionnaire builder: pillar weights add up to 100; only yes/no and scale questions are scored.
    send(post("/v1/supply-chain/templates"), director, template("T " + tag, 40, 30, 20, QUESTIONS)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.weightE").exists());
    send(post("/v1/supply-chain/templates"), director, template("T " + tag, 40, 30, 30, question("E", "q", "yes_no", null, 1, false) + ","
        + question("E", "n", "number", null, 2, false))).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['questions[0].positiveAnswer']").exists()).andExpect(jsonPath("$.errors['questions[1].weight']").exists());
    JsonNode draft = data(send(post("/v1/supply-chain/templates"), director, template("Draft " + tag, 40, 30, 30, QUESTIONS)).andExpect(status().isCreated()));
    String templateId = publishedTemplate(director, "SME screen " + tag);
    send(put("/v1/supply-chain/templates/" + templateId), director, template("SME screen " + tag, 40, 30, 30, QUESTIONS).replace("]}", "],\"version\":1}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-STATE"));

    // Simplified onboarding: no email needed; full onboarding needs one (the link is emailed).
    send(post("/v1/supply-chain/suppliers"), manager, supplier("Okoro Fabrication " + tag, "full", null)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.contactEmail").exists());
    send(post("/v1/supply-chain/suppliers"), manager, supplier("X " + tag, "simplified", null).replace("\"NG\"", "\"XX\"")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.countryCode").exists());
    JsonNode supplier = data(send(post("/v1/supply-chain/suppliers"), manager, supplier("Okoro Fabrication " + tag, "simplified", null))
        .andExpect(status().isCreated()));
    String supplierId = supplier.path("id").asText();
    assertThat(supplier.path("local").asBoolean()).as("same country as the tenant").isTrue();
    send(post("/v1/supply-chain/suppliers"), manager, supplier("okoro fabrication " + tag, "simplified", null)).andExpect(status().isConflict());

    // Send: a draft template can't be sent; the link is returned once.
    send(post("/v1/supply-chain/questionnaires"), manager, "{\"supplierId\":\"" + supplierId + "\",\"templateId\":\"" + draft.path("id").asText()
        + "\",\"dueDate\":\"" + TODAY.plusDays(14) + "\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.templateId").exists());
    JsonNode sent = data(send(post("/v1/supply-chain/questionnaires"), manager, "{\"supplierId\":\"" + supplierId + "\",\"templateId\":\"" + templateId
        + "\",\"dueDate\":\"" + TODAY.plusDays(14) + "\"}").andExpect(status().isCreated()));
    assertThat(sent.path("link").asText()).contains("/supplier-questionnaire/");
    assertThat(sent.path("emailStatus").asText()).isEqualTo("no_email");
    String token = token(sent);
    String qid = sent.path("questionnaireId").asText();
    assertThat(read("/v1/supply-chain/suppliers/" + supplierId, manager).path("status").asText()).isEqualTo("active");
    // Only the token's hash is stored.
    assertThat(ownerValue("select token_hash from supply_chain.questionnaires where id = '" + qid + "'")).doesNotContain(token).hasSize(64);

    // The supplier opens the link with no account and sees this questionnaire only.
    JsonNode pub = data(mvc.perform(get("/v1/public/supplier-questionnaires/" + token)).andExpect(status().isOk()));
    assertThat(pub.path("supplierName").asText()).isEqualTo("Okoro Fabrication " + tag);
    assertThat(pub.path("questions").size()).isEqualTo(6);
    assertThat(pub.toString()).doesNotContain("Supplier A").doesNotContain("esgScore");
    mvc.perform(get("/v1/public/supplier-questionnaires/" + "A".repeat(43))).andExpect(status().isNotFound());
    mvc.perform(get("/v1/public/supplier-questionnaires/short")).andExpect(status().isNotFound());
    Map<String, String> q = new HashMap<>();
    pub.path("questions").forEach(x -> q.put(x.path("question").asText(), x.path("id").asText()));

    // Answers: the kind of answer must fit the question; evidence must be uploaded through this link.
    String body = "{\"answers\":[{\"questionId\":\"" + q.get("How well do you manage waste?") + "\",\"choice\":\"yes\"}]}";
    send(put("/v1/public/supplier-questionnaires/" + token + "/answers"), null, body).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['answers[0].choice']").exists());
    send(put("/v1/public/supplier-questionnaires/" + token + "/answers"), null, "{\"answers\":[{\"questionId\":\"" + q.get("Do you have a health and safety policy?")
        + "\",\"choice\":\"yes\",\"fileId\":\"a0000000-0000-4000-8000-0000000004f1\"}]}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['answers[0].fileId']").exists());
    String upload = mvc.perform(multipart("/v1/public/supplier-questionnaires/" + token + "/evidence")
        .file(new MockMultipartFile("file", "hs-policy.pdf", "application/pdf", "%PDF-1.7 H&S policy".getBytes(StandardCharsets.US_ASCII))))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String fileId = json.readTree(upload).path("data").path("id").asText();
    send(put("/v1/public/supplier-questionnaires/" + token + "/answers"), null, "{\"answers\":[{\"questionId\":\"" + q.get("Do you measure your energy use?")
        + "\",\"choice\":\"yes\"}]}").andExpect(status().isOk());
    send(post("/v1/public/supplier-questionnaires/" + token + "/submit"), null, "").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.answers").value(org.hamcrest.Matchers.containsString("still unanswered")));
    send(put("/v1/public/supplier-questionnaires/" + token + "/answers"), null, "{\"answers\":["
        + "{\"questionId\":\"" + q.get("How well do you manage waste?") + "\",\"choice\":\"4\"},"
        + "{\"questionId\":\"" + q.get("Diesel used last year (litres)") + "\",\"number\":12500},"
        + "{\"questionId\":\"" + q.get("Has child labour been found in your operations?") + "\",\"choice\":\"no\"},"
        + "{\"questionId\":\"" + q.get("Do you have a health and safety policy?") + "\",\"choice\":\"yes\",\"fileId\":\"" + fileId + "\",\"comment\":\"Signed 2025\"},"
        + "{\"questionId\":\"" + q.get("How mature is your anti-bribery programme?") + "\",\"choice\":\"3\"}]}").andExpect(status().isOk());
    long before = inbox(roleUserId("ESG_DIRECTOR"), "SUPPLIER_QUESTIONNAIRE_SUBMITTED");
    JsonNode submitted = data(send(post("/v1/public/supplier-questionnaires/" + token + "/submit"), null, "").andExpect(status().isOk()));
    assertThat(submitted.path("status").asText()).isEqualTo("submitted");
    assertThat(inbox(roleUserId("ESG_DIRECTOR"), "SUPPLIER_QUESTIONNAIRE_SUBMITTED")).isEqualTo(before + 1);
    send(put("/v1/public/supplier-questionnaires/" + token + "/answers"), null, "{\"answers\":[]}").andExpect(status().isConflict());

    // The scorecard (golden values in SupplierScoringTest): E 93.8, S 100, G 50 → 82.5, low risk.
    JsonNode qn = read("/v1/supply-chain/questionnaires/" + qid, manager);
    assertThat(qn.path("scoreE").decimalValue()).isEqualByComparingTo("93.8");
    assertThat(qn.path("scoreS").decimalValue()).isEqualByComparingTo("100.0");
    assertThat(qn.path("scoreG").decimalValue()).isEqualByComparingTo("50.0");
    assertThat(qn.path("score").decimalValue()).isEqualByComparingTo("82.5");
    assertThat(qn.path("riskLevel").asText()).isEqualTo("low");
    assertThat(qn.path("answers").toString()).contains("hs-policy.pdf").contains("Signed 2025");
    send(post("/v1/supply-chain/questionnaires/" + qid + "/actions"), manager, "{\"action\":\"review\",\"version\":" + qn.path("version").asLong() + "}")
        .andExpect(status().isForbidden());
    qn = data(send(post("/v1/supply-chain/questionnaires/" + qid + "/actions"), director, "{\"action\":\"review\",\"note\":\"Good\",\"version\":"
        + qn.path("version").asLong() + "}").andExpect(status().isOk()));
    assertThat(qn.path("status").asText()).isEqualTo("reviewed");
    JsonNode s = read("/v1/supply-chain/suppliers/" + supplierId, manager);
    assertThat(s.path("esgScore").decimalValue()).isEqualByComparingTo("82.5");
    assertThat(s.path("riskLevel").asText()).isEqualTo("low");
    assertThat(s.path("history").get(0).path("score").decimalValue()).isEqualByComparingTo("82.5");
    // A reviewed questionnaire's link is closed.
    mvc.perform(get("/v1/public/supplier-questionnaires/" + token)).andExpect(status().isNotFound());

    // Corrective action: raise → assign → implement → verify (someone else) → close.
    String child = q.get("Has child labour been found in your operations?");
    send(post("/v1/supply-chain/actions"), manager, "{\"supplierId\":\"" + supplierId + "\",\"title\":\"x\",\"severity\":\"high\",\"dueDate\":\""
        + TODAY.plusDays(30) + "\",\"questionId\":\"" + child + "\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.questionId").exists());
    JsonNode capa = data(send(post("/v1/supply-chain/actions"), manager, "{\"supplierId\":\"" + supplierId + "\",\"questionnaireId\":\"" + qid
        + "\",\"questionId\":\"" + child + "\",\"title\":\"Age verification at the gate\",\"severity\":\"high\",\"dueDate\":\"" + TODAY.plusDays(30)
        + "\",\"ownerUserId\":\"" + roleUserId("ESG_MANAGER") + "\"}").andExpect(status().isCreated()));
    assertThat(capa.path("status").asText()).isEqualTo("assigned");
    assertThat(capa.path("questionText").asText()).isEqualTo("Has child labour been found in your operations?");
    String ap = "/v1/supply-chain/actions/" + capa.path("id").asText() + "/steps";
    send(post(ap), manager, "{\"action\":\"implement\",\"version\":" + capa.path("version").asLong() + "}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.note").exists());
    capa = data(send(post(ap), manager, "{\"action\":\"implement\",\"note\":\"ID checks at the gate since March\",\"version\":" + capa.path("version").asLong()
        + "}").andExpect(status().isOk()));
    send(post(ap), manager, "{\"action\":\"verify\",\"version\":" + capa.path("version").asLong() + "}").andExpect(status().isForbidden());
    capa = data(send(post(ap), director, "{\"action\":\"reject\",\"note\":\"Show the gate log\",\"version\":" + capa.path("version").asLong() + "}")
        .andExpect(status().isOk()));
    assertThat(capa.path("status").asText()).isEqualTo("assigned");
    capa = data(send(post(ap), manager, "{\"action\":\"implement\",\"note\":\"Gate log attached\",\"version\":" + capa.path("version").asLong() + "}"));
    capa = data(send(post(ap), director, "{\"action\":\"verify\",\"note\":\"Seen on site\",\"version\":" + capa.path("version").asLong() + "}")
        .andExpect(status().isOk()));
    capa = data(send(post(ap), director, "{\"action\":\"close\",\"version\":" + capa.path("version").asLong() + "}").andExpect(status().isOk()));
    assertThat(capa.path("status").asText()).isEqualTo("closed");
    assertThat(capa.path("verifiedByName").asText()).isEqualTo("Esg Director Fixture");

    // Whoever recorded the fix can't verify it.
    JsonNode own = data(send(post("/v1/supply-chain/actions"), director, "{\"supplierId\":\"" + supplierId + "\",\"title\":\"Waste log\",\"severity\":\"low\","
        + "\"dueDate\":\"" + TODAY.plusDays(7) + "\",\"ownerUserId\":\"" + roleUserId("ESG_DIRECTOR") + "\"}").andExpect(status().isCreated()));
    String op = "/v1/supply-chain/actions/" + own.path("id").asText() + "/steps";
    own = data(send(post(op), director, "{\"action\":\"implement\",\"note\":\"Done\",\"version\":" + own.path("version").asLong() + "}"));
    assertThat(own.path("blockedActions").path("verify").asText()).contains("someone else");
    send(post(op), director, "{\"action\":\"verify\",\"version\":" + own.path("version").asLong() + "}").andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ESG-409-SOD"));

    // Automated follow-up: due in 7 days → one reminder; running again the same day sends nothing more for it.
    JsonNode due = data(send(post("/v1/supply-chain/actions"), director, "{\"supplierId\":\"" + supplierId + "\",\"title\":\"Install meters\",\"severity\":\"medium\","
        + "\"dueDate\":\"" + TODAY.plusDays(7) + "\",\"ownerUserId\":\"" + roleUserId("ESG_MANAGER") + "\"}").andExpect(status().isCreated()));
    long reminders = inbox(roleUserId("ESG_MANAGER"), "CORRECTIVE_ACTION_DUE");
    actionService.runAll(Clock.systemUTC());
    assertThat(inbox(roleUserId("ESG_MANAGER"), "CORRECTIVE_ACTION_DUE")).isGreaterThan(reminders);
    long afterFirst = inbox(roleUserId("ESG_MANAGER"), "CORRECTIVE_ACTION_DUE");
    actionService.runAll(Clock.systemUTC());
    assertThat(inbox(roleUserId("ESG_MANAGER"), "CORRECTIVE_ACTION_DUE")).isEqualTo(afterFirst);
    // Ten days on it's overdue: one more reminder.
    actionService.runAll(Clock.offset(Clock.systemUTC(), Duration.ofDays(10)));
    assertThat(ownerValue("select count(*) from supply_chain.action_reminders where action_id = '" + due.path("id").asText() + "'")).isEqualTo("2");
    assertThat(read("/v1/supply-chain/overview", director).path("openActions").asInt()).isPositive();
  }

  @Test
  void aLinkOpensOnlyItsOwnQuestionnaireAndCanBeReturnedOrReplaced() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    String tag = Long.toString(System.nanoTime(), 36);
    String templateId = publishedTemplate(director, "Link test " + tag);
    String a = data(send(post("/v1/supply-chain/suppliers"), director, supplier("A " + tag, "simplified", null))).path("id").asText();
    String b = data(send(post("/v1/supply-chain/suppliers"), director, supplier("B " + tag, "full", "esg@b.example"))).path("id").asText();
    JsonNode sa = data(send(post("/v1/supply-chain/questionnaires"), director, "{\"supplierId\":\"" + a + "\",\"templateId\":\"" + templateId
        + "\",\"dueDate\":\"" + TODAY.plusDays(10) + "\",\"title\":\"For A\"}").andExpect(status().isCreated()));
    JsonNode sb = data(send(post("/v1/supply-chain/questionnaires"), director, "{\"supplierId\":\"" + b + "\",\"templateId\":\"" + templateId
        + "\",\"dueDate\":\"" + TODAY.plusDays(10) + "\",\"title\":\"For B\"}").andExpect(status().isCreated()));
    assertThat(sb.path("emailStatus").asText()).as("email isn't configured in tests").isIn("not_configured", "queued");
    JsonNode viewA = data(mvc.perform(get("/v1/public/supplier-questionnaires/" + token(sa))).andExpect(status().isOk()));
    assertThat(viewA.path("title").asText()).isEqualTo("For A");
    assertThat(viewA.toString()).doesNotContain("B " + tag).doesNotContain("For B");
    // A's link can't answer B's questions (question ids of another questionnaire are refused only if foreign; same template → B's answers untouched).
    String someQuestion = viewA.path("questions").get(0).path("id").asText();
    send(put("/v1/public/supplier-questionnaires/" + token(sa) + "/answers"), null, "{\"answers\":[{\"questionId\":\"" + someQuestion + "\",\"choice\":\"yes\"}]}")
        .andExpect(status().isOk());
    assertThat(read("/v1/supply-chain/questionnaires/" + sb.path("questionnaireId").asText(), director).path("answers").size()).isZero();
    // A question from a different template is refused.
    send(put("/v1/public/supplier-questionnaires/" + token(sa) + "/answers"), null, "{\"answers\":[{\"questionId\":\"a0000000-0000-4000-8000-000000000203\","
        + "\"choice\":\"yes\"}]}").andExpect(status().isBadRequest());
    // Resend replaces the link: the old one stops working.
    JsonNode qa = read("/v1/supply-chain/questionnaires/" + sa.path("questionnaireId").asText(), director);
    JsonNode again = data(mvc.perform(post("/v1/supply-chain/questionnaires/" + qa.path("id").asText() + "/resend?version=" + qa.path("version").asLong())
        .header("Authorization", director)).andExpect(status().isOk()));
    mvc.perform(get("/v1/public/supplier-questionnaires/" + token(sa))).andExpect(status().isNotFound());
    mvc.perform(get("/v1/public/supplier-questionnaires/" + token(again))).andExpect(status().isOk());
    // Returned with a note: the supplier sees the note and can answer again.
    JsonNode viewAgain = data(mvc.perform(get("/v1/public/supplier-questionnaires/" + token(again))));
    Map<String, String> q = new HashMap<>();
    viewAgain.path("questions").forEach(x -> q.put(x.path("question").asText(), x.path("id").asText()));
    StringBuilder all = new StringBuilder();
    for (var e : q.entrySet()) {
      String k = e.getKey();
      String v = k.startsWith("How") ? "\"choice\":\"3\"" : k.startsWith("Diesel") ? "\"number\":1" : "\"choice\":\"no\"";
      all.append(all.isEmpty() ? "" : ",").append("{\"questionId\":\"").append(e.getValue()).append("\",").append(v).append("}");
    }
    send(put("/v1/public/supplier-questionnaires/" + token(again) + "/answers"), null, "{\"answers\":[" + all + "]}").andExpect(status().isOk());
    send(post("/v1/public/supplier-questionnaires/" + token(again) + "/submit"), null, "").andExpect(status().isOk());
    qa = read("/v1/supply-chain/questionnaires/" + qa.path("id").asText(), director);
    assertThat(qa.path("gaps").toString()).contains("evidence required but not attached");
    send(post("/v1/supply-chain/questionnaires/" + qa.path("id").asText() + "/actions"), director, "{\"action\":\"return\",\"version\":" + qa.path("version").asLong() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.note").exists());
    send(post("/v1/supply-chain/questionnaires/" + qa.path("id").asText() + "/actions"), director, "{\"action\":\"return\",\"note\":\"Attach your H&S policy\","
        + "\"version\":" + qa.path("version").asLong() + "}").andExpect(status().isOk());
    JsonNode returned = data(mvc.perform(get("/v1/public/supplier-questionnaires/" + token(again))).andExpect(status().isOk()));
    assertThat(returned.path("status").asText()).isEqualTo("in_progress");
    assertThat(returned.path("reviewNote").asText()).isEqualTo("Attach your H&S policy");
    // Cancel closes the link.
    qa = read("/v1/supply-chain/questionnaires/" + qa.path("id").asText(), director);
    send(post("/v1/supply-chain/questionnaires/" + qa.path("id").asText() + "/actions"), director, "{\"action\":\"cancel\",\"version\":" + qa.path("version").asLong() + "}")
        .andExpect(status().isOk());
    mvc.perform(get("/v1/public/supplier-questionnaires/" + token(again))).andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------- spend → Scope 3 Category 1

  @Test
  void supplierSpendBecomesScope3Category1InCarbonAndCanBeImported() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    String tag = Long.toString(System.nanoTime(), 36);
    String supplierId = data(send(post("/v1/supply-chain/suppliers"), director, supplier("Paper Co " + tag, "simplified", null))).path("id").asText();
    int year = TODAY.getYear() - 1;
    String body = "{\"supplierId\":\"" + supplierId + "\",\"orgId\":\"" + ORG_SITE_1 + "\",\"reportingPeriodStart\":\"" + year + "-01-01\","
        + "\"reportingPeriodEnd\":\"" + year + "-03-31\",\"amount\":2000,\"currency\":\"USD\",\"sourceActivity\":\"spend_paper_stationery\","
        + "\"description\":\"Office paper\"}";
    send(post("/v1/supply-chain/spend"), director, body.replace("spend_paper_stationery", "spend_computers")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.sourceActivity").exists());
    send(post("/v1/supply-chain/spend"), director, body.replace(year + "-01-01", "2020-01-01").replace(year + "-03-31", "2020-03-31"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-LOCKED"));
    send(post("/v1/supply-chain/spend"), director, body.replace(year + "-03-31", TODAY.plusDays(5).toString())).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.reportingPeriodEnd").exists());
    send(post("/v1/supply-chain/spend"), bearerForRole("AUDITOR"), body).andExpect(status().isForbidden());

    // 2,000 USD × 0.296 kgCO2e/USD (EPA EEIO, stationery) = 592 kg = 0.592 t, Scope 3 Category 1.
    JsonNode line = data(send(post("/v1/supply-chain/spend"), director, body).andExpect(status().isCreated()));
    assertThat(line.path("co2eTonnes").decimalValue()).isEqualByComparingTo("0.592");
    JsonNode record = read("/v1/carbon/emissions/" + line.path("emissionRecordId").asText(), director);
    assertThat(record.path("scope").asText()).isEqualTo("scope_3");
    assertThat(record.path("scope3Category").asInt()).isEqualTo(1);
    assertThat(record.path("recordSource").asText()).isEqualTo("supply_chain");
    // Carbon won't edit it directly; the spend line drives it.
    send(put("/v1/carbon/emissions/" + record.path("id").asText()), director, "{\"orgId\":\"" + ORG_SITE_1 + "\",\"scope\":\"scope_3\",\"scope3Category\":1,"
        + "\"calculationMethod\":\"spend_based\",\"sourceActivity\":\"spend_paper_stationery\",\"spendAmount\":1,\"spendCurrency\":\"USD\","
        + "\"reportingPeriodStart\":\"" + year + "-01-01\",\"reportingPeriodEnd\":\"" + year + "-03-31\",\"version\":" + record.path("version").asLong() + "}")
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-STATE"));
    line = data(send(put("/v1/supply-chain/spend/" + line.path("id").asText()), director, body.replace("\"amount\":2000", "\"amount\":3000")
        .replace("}", ",\"version\":" + line.path("version").asLong() + "}")).andExpect(status().isOk()));
    assertThat(line.path("co2eTonnes").decimalValue()).isEqualByComparingTo("0.888");
    assertThat(read("/v1/supply-chain/overview?year=" + year, director).path("scope3Cat1Tonnes").decimalValue()).isGreaterThanOrEqualTo(new java.math.BigDecimal("0.888"));
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/v1/supply-chain/spend/" + line.path("id").asText() + "?version="
        + line.path("version").asLong()).header("Authorization", director)).andExpect(status().isNoContent());
    mvc.perform(get("/v1/carbon/emissions/" + record.path("id").asText()).header("Authorization", director)).andExpect(status().isNotFound());

    // Import: each row stands alone; problems are reported per row.
    String csv = "supplier,organisation,period_start,period_end,amount,currency,category,description\n"
        + "Paper Co " + tag + ",A Site 1," + year + "-04-01," + year + "-06-30,1000,USD,Paper and stationery (spend),Q2 paper\n"
        + "Nobody Ltd,A Site 1," + year + "-04-01," + year + "-06-30,1000,USD,spend_paper_stationery,\n"
        + "Paper Co " + tag + ",A Site 1," + year + "-04-01," + year + "-06-30,abc,USD,spend_road_freight,\n";
    JsonNode imported = data(mvc.perform(multipart("/v1/supply-chain/spend/import").file(new MockMultipartFile("file", "spend.csv", "text/csv",
        csv.getBytes(StandardCharsets.UTF_8))).header("Authorization", director)).andExpect(status().isOk()));
    assertThat(imported.path("rows").asInt()).isEqualTo(3);
    assertThat(imported.path("created").asInt()).isEqualTo(1);
    assertThat(imported.path("errors").get(0).path("row").asInt()).isEqualTo(3);
    assertThat(imported.path("errors").get(0).path("message").asText()).contains("isn't in the supplier register");
    assertThat(imported.path("errors").get(1).path("message").asText()).contains("amount").contains("category");
    assertThat(read("/v1/supply-chain/spend?supplierId=" + supplierId, director).size()).isEqualTo(1);
    mvc.perform(multipart("/v1/supply-chain/spend/import").file(new MockMultipartFile("file", "spend.csv", "text/csv",
        "supplier,amount\nx,1\n".getBytes(StandardCharsets.UTF_8))).header("Authorization", director)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.file").value(org.hamcrest.Matchers.containsString("missing column")));
  }

  @Test
  void readersReadWritersWriteAndOtherTenantsCantSeeTheModule() throws Exception {
    mvc.perform(get("/v1/supply-chain/overview").header("Authorization", bearerForRole("AUDITOR"))).andExpect(status().isOk());
    send(post("/v1/supply-chain/suppliers"), bearerForRole("AUDITOR"), supplier("Z", "simplified", null)).andExpect(status().isForbidden());
    mvc.perform(get("/v1/supply-chain/suppliers").header("Authorization", bearerForRole("INVESTOR_VIEWER"))).andExpect(status().isForbidden());
    mvc.perform(get("/v1/supply-chain/overview").header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B))).andExpect(status().isForbidden());
    JsonNode dash = read("/v1/dashboard/executive", bearerForRole("ESG_DIRECTOR"));
    assertThat(dash.path("supplyChain").isObject()).isTrue();
    // The supplier register evidences the value chain (GRI 2-6) for the current year.
    for (JsonNode r : read("/v1/compliance/gaps?framework=GRI&year=" + TODAY.getYear(), bearerForRole("COMPLIANCE_OFFICER")).path("requirements")) {
      if ("2-6".equals(r.path("code").asText())) {
        assertThat(r.path("status").asText()).isNotEqualTo("missing");
      }
    }
  }

  // ---------------------------------------------------------------- helpers

  private static long inbox(String userId, String type) throws Exception {
    return Long.parseLong(ownerValue("select count(*) from notifications.notification_queue where user_id = '" + userId + "' and notification_type = '" + type + "'"));
  }

  private static String ownerValue(String sql) throws Exception {
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      ResultSet rs = st.executeQuery(sql);
      rs.next();
      return rs.getString(1);
    }
  }
}
