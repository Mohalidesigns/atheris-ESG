package com.esg.investor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * WP-40 M10 Investor Portal through the API: journey 22 (an investor sees only published scorecards and their own data
 * room), journey 23 (an IFC report generated from tenant data, approved and shared), sustainable finance instruments,
 * IRIS+ impact metrics, and who may do what.
 */
class InvestorApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;

  private static final int YEAR = LocalDate.now(ZoneId.of("Africa/Lagos")).getYear();
  private static final String INVESTOR_EMAIL = "investor_viewer@fixture.test";

  private JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  private ResultActions send(MockHttpServletRequestBuilder b, String token, String body) throws Exception {
    return mvc.perform(b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode read(String path, String token) throws Exception {
    return data(mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk()));
  }

  private static boolean contains(JsonNode list, String id) {
    for (JsonNode x : list) {
      if (id.equals(x.path("id").asText())) {
        return true;
      }
    }
    return false;
  }

  private static byte[] pdf(String text) throws Exception {
    try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        cs.newLineAtOffset(72, 700);
        cs.showText(text);
        cs.endText();
      }
      doc.save(out);
      return out.toByteArray();
    }
  }

  private static String text(byte[] pdf) throws Exception {
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(doc);
    }
  }

  private String upload(String token, String name, byte[] content) throws Exception {
    return data(mvc.perform(multipart("/v1/files").file(new MockMultipartFile("file", name, "application/pdf", content)).param("resourceType", "data_room")
        .header("Authorization", token)).andExpect(status().isCreated())).path("id").asText();
  }

  // ---------------------------------------------------------------- journey 22

  @Test
  void journey22AnInvestorSeesOnlyPublishedScorecardsAndTheirOwnDataRoom() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    String admin = bearerForRole("TENANT_ADMIN");
    String investor = bearerForRole("INVESTOR_VIEWER");
    String tag = Long.toString(System.nanoTime(), 36);

    // Weights add up to 100 (investor:configure).
    JsonNode w = read("/v1/investor/settings", director);
    send(put("/v1/investor/settings"), director, "{\"weightE\":50,\"weightS\":30,\"weightG\":30,\"version\":" + w.path("version").asLong() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.weightE").exists());
    send(put("/v1/investor/settings"), bearerForRole("ESG_MANAGER"), "{\"weightE\":40,\"weightS\":30,\"weightG\":30}").andExpect(status().isForbidden());

    // A scorecard is generated as a draft: three pillars, each with the components it was built from.
    JsonNode preview = read("/v1/investor/scorecards/preview?year=" + YEAR, director);
    assertThat(preview.path("pillars").size()).isEqualTo(3);
    assertThat(preview.path("pillars").get(2).path("components").toString()).contains("Framework completeness");
    send(post("/v1/investor/scorecards"), director, "{\"year\":" + YEAR + ",\"title\":\"\"}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.title").exists());
    JsonNode sc = data(send(post("/v1/investor/scorecards"), director, "{\"year\":" + YEAR + ",\"title\":\"ESG scorecard " + tag
        + "\",\"commentary\":\"First scorecard for the investment committee\"}").andExpect(status().isCreated()));
    String id = sc.path("id").asText();
    assertThat(sc.path("status").asText()).isEqualTo("draft");
    assertThat(sc.path("total").decimalValue()).isEqualByComparingTo(preview.path("total").decimalValue());
    assertThat(sc.path("grade").asText()).isEqualTo(ScorecardMath.grade(sc.path("total").decimalValue()));

    // The investor sees no draft.
    assertThat(contains(read("/v1/investor/scorecards", investor), id)).isFalse();
    mvc.perform(get("/v1/investor/scorecards/" + id).header("Authorization", investor)).andExpect(status().isNotFound());

    // Whoever generated it can't publish it; another manager does.
    assertThat(sc.path("blockedActions").path("publish").asText()).contains("someone else");
    send(post("/v1/investor/scorecards/" + id + "/actions"), director, "{\"action\":\"publish\",\"version\":" + sc.path("version").asLong() + "}")
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-SOD"));
    sc = data(send(post("/v1/investor/scorecards/" + id + "/actions"), admin, "{\"action\":\"publish\",\"version\":" + sc.path("version").asLong() + "}")
        .andExpect(status().isOk()));
    assertThat(sc.path("status").asText()).isEqualTo("published");
    JsonNode seen = read("/v1/investor/scorecards/" + id, investor);
    assertThat(seen.path("actions").size()).isZero();
    JsonNode dash = read("/v1/investor/dashboards", investor);
    assertThat(dash.path("manager").asBoolean()).isFalse();
    assertThat(dash.path("history").toString()).contains(id);

    // What investors saw can't change underneath them.
    assertThatThrownBy(() -> owner("update investor.scorecards set total = 99 where id = '" + id + "'")).isInstanceOf(SQLException.class)
        .hasMessageContaining("published scorecard can't change");

    // Withdrawn with a reason: gone from the investor's view.
    send(post("/v1/investor/scorecards/" + id + "/actions"), director, "{\"action\":\"withdraw\",\"version\":" + sc.path("version").asLong() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.note").exists());
    send(post("/v1/investor/scorecards/" + id + "/actions"), director, "{\"action\":\"withdraw\",\"note\":\"Superseded by the audited figures\",\"version\":"
        + sc.path("version").asLong() + "}").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("withdrawn"));
    assertThat(contains(read("/v1/investor/scorecards", investor), id)).isFalse();

    // Data rooms: members must be investor users; the expiry can't be in the past.
    String investorId = roleUserId("INVESTOR_VIEWER");
    assertThat(read("/v1/investor/people", director).toString()).contains(INVESTOR_EMAIL).doesNotContain("esg_manager@fixture.test");
    String room = "{\"name\":\"Due diligence " + tag + "\",\"purpose\":\"due_diligence\",\"expiresOn\":\"" + LocalDate.now().plusDays(30)
        + "\",\"status\":\"open\",\"watermark\":true,\"memberIds\":[\"" + investorId + "\"]}";
    send(post("/v1/investor/data-rooms"), director, room.replace(investorId, roleUserId("ESG_MANAGER"))).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.memberIds").exists());
    send(post("/v1/investor/data-rooms"), director, room.replace(LocalDate.now().plusDays(30).toString(), "2020-01-01"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.expiresOn").exists());
    send(post("/v1/investor/data-rooms"), investor, room).andExpect(status().isForbidden());
    JsonNode r = data(send(post("/v1/investor/data-rooms"), director, room).andExpect(status().isCreated()));
    String roomId = r.path("id").asText();
    JsonNode other = data(send(post("/v1/investor/data-rooms"), director, room.replace("Due diligence", "Board pack").replace("[\"" + investorId + "\"]", "[]"))
        .andExpect(status().isCreated()));
    // A PDF that can't be watermarked (here: not really a PDF) is refused up front, not handed out unmarked.
    String broken = upload(director, "broken.pdf", "%PDF-1.4 not really".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    send(post("/v1/investor/data-rooms/" + roomId + "/documents"), director, "{\"fileId\":\"" + broken + "\",\"title\":\"Broken\"}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.fileId").value(org.hamcrest.Matchers.containsString("can't be watermarked")));
    String fileId = upload(director, "esg-policy.pdf", pdf("ESG policy, approved by the board"));
    r = data(send(post("/v1/investor/data-rooms/" + roomId + "/documents"), director, "{\"fileId\":\"" + fileId + "\",\"title\":\"ESG policy\"}")
        .andExpect(status().isCreated()));
    String docId = r.path("documents").get(0).path("id").asText();

    // The investor sees their room only; opening and downloading are logged; the PDF carries their name.
    JsonNode rooms = read("/v1/investor/data-rooms", investor);
    assertThat(contains(rooms, roomId)).isTrue();
    assertThat(contains(rooms, other.path("id").asText())).isFalse();
    mvc.perform(get("/v1/investor/data-rooms/" + other.path("id").asText()).header("Authorization", investor)).andExpect(status().isNotFound());
    JsonNode opened = read("/v1/investor/data-rooms/" + roomId, investor);
    assertThat(opened.path("members").size()).as("investors don't see who else is in the room").isZero();
    byte[] got = mvc.perform(get("/v1/investor/data-rooms/" + roomId + "/documents/" + docId + "/download").header("Authorization", investor))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    assertThat(text(got)).contains("ESG policy, approved by the board").contains(INVESTOR_EMAIL).contains("Due diligence " + tag);
    mvc.perform(get("/v1/investor/data-rooms/" + roomId + "/access-log").header("Authorization", investor)).andExpect(status().isForbidden());
    JsonNode log = read("/v1/investor/data-rooms/" + roomId + "/access-log", director);
    assertThat(log.toString()).contains("\"action\":\"download\"").contains("\"action\":\"view\"").contains(INVESTOR_EMAIL).contains("\"watermarked\":true");

    // Closed: the investor is refused (and that is logged too).
    send(put("/v1/investor/data-rooms/" + roomId), director, room.replace("\"open\"", "\"closed\"").replace("}", ",\"version\":" + r.path("version").asLong() + "}"))
        .andExpect(status().isOk());
    mvc.perform(get("/v1/investor/data-rooms/" + roomId).header("Authorization", investor)).andExpect(status().isForbidden());
    assertThat(contains(read("/v1/investor/data-rooms", investor), roomId)).isFalse();
    assertThat(read("/v1/investor/data-rooms/" + roomId + "/access-log", director).toString()).contains("\"action\":\"refused\"");
  }

  // ---------------------------------------------------------------- journey 23

  private JsonNode approval(JsonNode report, String token, String action) throws Exception {
    int n = report.path("approval").path("currentStep").asInt();
    String step = report.path("approval").path("steps").get(n - 1).path("id").asText();
    return data(send(put("/v1/reports/" + report.path("id").asText() + "/approval-steps/" + step), token, "{\"version\":" + report.path("version").asLong()
        + ",\"action\":\"" + action + "\"}").andExpect(status().isOk()));
  }

  @Test
  void journey23AnIfcReportIsGeneratedFromTenantDataApprovedAndShared() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    String investor = bearerForRole("INVESTOR_VIEWER");
    JsonNode templates = read("/v1/investor/dfi-templates", director);
    assertThat(templates.toString()).contains("dfi_ifc_ps").contains("dfi_afdb_iss").contains("pending_sme");
    send(post("/v1/investor/dfi-reports"), director, "{\"template\":\"dfi_bii\",\"year\":" + YEAR + "}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.template").exists());
    send(post("/v1/investor/dfi-reports"), investor, "{\"template\":\"dfi_ifc_ps\",\"year\":" + YEAR + "}").andExpect(status().isForbidden());

    JsonNode report = data(send(post("/v1/investor/dfi-reports"), director, "{\"template\":\"dfi_ifc_ps\",\"year\":" + YEAR + "}")
        .andExpect(status().isCreated()));
    assertThat(report.path("title").asText()).isEqualTo("IFC annual E&S performance report " + YEAR);
    assertThat(report.path("sections").size()).isEqualTo(8);
    assertThat(report.path("sections").get(0).path("title").asText()).startsWith("PS1");
    // Filled from the tenant's data (social and carbon figures resolved, or marked not reported).
    assertThat(report.path("snapshot").has("social:ltifr")).isTrue();
    assertThat(report.path("snapshot").has("carbon:scope1")).isTrue();

    // A draft can't be shared; approved (manager submits; director, compliance, board approve), it can.
    send(post("/v1/investor/shared-reports"), director, "{\"reportId\":\"" + report.path("id").asText() + "\"}").andExpect(status().isConflict());
    report = data(send(post("/v1/reports/" + report.path("id").asText() + "/approve"), bearerForRole("ESG_MANAGER"), "{\"version\":"
        + report.path("version").asLong() + "}").andExpect(status().isOk()));
    report = approval(report, director, "approve");
    report = approval(report, bearerForRole("COMPLIANCE_OFFICER"), "approve");
    report = approval(report, bearerForRole("BOARD_MEMBER"), "approve");
    assertThat(report.path("status").asText()).isEqualTo("approved");
    JsonNode shared = data(send(post("/v1/investor/shared-reports"), director, "{\"reportId\":\"" + report.path("id").asText()
        + "\",\"note\":\"For the annual monitoring visit\"}").andExpect(status().isCreated()));
    assertThat(shared.path("versionLabel").asText()).isEqualTo("v1.0");

    // The investor downloads it, stamped with their name; once revoked it is gone.
    assertThat(contains(read("/v1/investor/shared-reports", investor), shared.path("id").asText())).isTrue();
    byte[] got = mvc.perform(get("/v1/investor/shared-reports/" + shared.path("id").asText() + "/download").header("Authorization", investor))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    assertThat(text(got)).contains("IFC annual E&S performance report").contains(INVESTOR_EMAIL);
    mvc.perform(delete("/v1/investor/shared-reports/" + shared.path("id").asText()).header("Authorization", investor)).andExpect(status().isForbidden());
    mvc.perform(delete("/v1/investor/shared-reports/" + shared.path("id").asText()).header("Authorization", director)).andExpect(status().isNoContent());
    assertThat(contains(read("/v1/investor/shared-reports", investor), shared.path("id").asText())).isFalse();
    mvc.perform(get("/v1/investor/shared-reports/" + shared.path("id").asText() + "/download").header("Authorization", investor))
        .andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------- instruments and impact

  private static String bond(String instrument, String name, String extra) {
    return "{\"instrument\":\"" + instrument + "\",\"name\":\"" + name + "\",\"issueDate\":\"2025-01-15\",\"maturityDate\":\"2032-01-15\",\"amount\":1000000,"
        + "\"currency\":\"NGN\",\"frameworkReference\":\"ICMA Green Bond Principles (2021)\",\"visibleToInvestors\":false,\"status\":\"active\"" + extra + "}";
  }

  @Test
  void instrumentsTrackUseOfProceedsTargetsAndShariahReview() throws Exception {
    String director = bearerForRole("ESG_DIRECTOR");
    String investor = bearerForRole("INVESTOR_VIEWER");
    String tag = Long.toString(System.nanoTime(), 36);

    send(post("/v1/investor/bonds"), director, bond("green_bond", "G " + tag, "").replace("2032-01-15", "2024-01-15")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.maturityDate").exists());
    send(post("/v1/investor/bonds"), director, bond("green_bond", "G " + tag, ",\"isin\":\"NG123\"")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.isin").exists());
    send(post("/v1/investor/bonds"), director, bond("sustainability_linked_bond", "S " + tag, "")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.kpiId").exists()).andExpect(jsonPath("$.errors.sptValue").exists());
    send(post("/v1/investor/bonds"), director, bond("green_sukuk", "K " + tag, "")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.shariahReviewer").exists());
    send(post("/v1/investor/bonds"), investor, bond("green_bond", "G " + tag, "")).andExpect(status().isForbidden());

    JsonNode g = data(send(post("/v1/investor/bonds"), director, bond("green_bond", "Green bond " + tag, ",\"isin\":\"NGFGN0000012\""))
        .andExpect(status().isCreated()));
    String gid = g.path("id").asText();
    String alloc = "{\"orgId\":\"" + ORG_SITE_1 + "\",\"category\":\"renewable_energy\",\"project\":\"Solar hybrid at Site 1\",\"amount\":400000,"
        + "\"allocatedOn\":\"" + YEAR + "-01-20\"}";
    g = data(send(post("/v1/investor/bonds/" + gid + "/allocations"), director, alloc).andExpect(status().isCreated()));
    assertThat(g.path("allocated").decimalValue()).isEqualByComparingTo("400000");
    assertThat(g.path("unallocated").decimalValue()).isEqualByComparingTo("600000");
    assertThat(g.path("allocatedPct").decimalValue()).isEqualByComparingTo("40.0");
    assertThat(g.path("byCategory").path("renewable_energy").decimalValue()).isEqualByComparingTo("400000");
    send(post("/v1/investor/bonds/" + gid + "/allocations"), director, alloc.replace("400000", "700000")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.amount").value(org.hamcrest.Matchers.containsString("600000")));
    send(post("/v1/investor/bonds/" + gid + "/allocations"), director, alloc.replace(YEAR + "-01-20", "2024-12-31")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.allocatedOn").exists());

    // An allocation dated in a locked period (FY2020) is refused by the period lock.
    JsonNode old = data(send(post("/v1/investor/bonds"), director, bond("green_bond", "Old bond " + tag, "").replace("2025-01-15", "2019-06-01"))
        .andExpect(status().isCreated()));
    send(post("/v1/investor/bonds/" + old.path("id").asText() + "/allocations"), director, alloc.replace(YEAR + "-01-20", "2020-03-01"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-LOCKED"));

    // Hidden from investors until marked visible.
    assertThat(contains(read("/v1/investor/bonds", investor), gid)).isFalse();
    mvc.perform(get("/v1/investor/bonds/" + gid).header("Authorization", investor)).andExpect(status().isNotFound());
    send(put("/v1/investor/bonds/" + gid), director, bond("green_bond", "Green bond " + tag, ",\"isin\":\"NGFGN0000012\",\"version\":" + g.path("version").asLong())
        .replace("\"visibleToInvestors\":false", "\"visibleToInvestors\":true")).andExpect(status().isOk());
    assertThat(read("/v1/investor/bonds/" + gid, investor).path("allocations").size()).isEqualTo(1);
    send(delete("/v1/investor/bonds/" + gid), director, "").andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-IN-USE"));

    // A sustainability-linked bond follows its KPI's target; pending until the observation year has a value.
    JsonNode kpi = data(send(post("/v1/analytics/kpis"), director, "{\"code\":\"slb_" + tag + "\",\"name\":\"SLB intensity " + tag + "\",\"pillar\":\"E\","
        + "\"metricA\":\"scope1_tco2e\",\"operator\":\"none\",\"multiplier\":1,\"unit\":\"tCO2e\",\"decimals\":1,\"direction\":\"lower_better\","
        + "\"sdgGoals\":[13],\"status\":\"active\"}").andExpect(status().isCreated()));
    JsonNode slb = data(send(post("/v1/investor/bonds"), director, bond("sustainability_linked_bond", "SLB " + tag, ",\"kpiId\":\"" + kpi.path("id").asText()
        + "\",\"sptValue\":500,\"sptObservationYear\":2099,\"couponStepUpBps\":25")).andExpect(status().isCreated()));
    assertThat(slb.path("spt").path("status").asText()).isEqualTo("pending");
    assertThat(slb.path("spt").path("kpiName").asText()).isEqualTo("SLB intensity " + tag);
    send(post("/v1/investor/bonds"), director, bond("green_bond", "G2 " + tag, ",\"kpiId\":\"" + kpi.path("id").asText() + "\""))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.kpiId").exists());
    JsonNode sukuk = data(send(post("/v1/investor/bonds"), director, bond("green_sukuk", "Sukuk " + tag, ",\"shariahReviewer\":\"Shariah Advisory Board\","
        + "\"shariahPronouncementOn\":\"2025-01-02\"")).andExpect(status().isCreated()));
    assertThat(sukuk.path("shariahReviewer").asText()).isEqualTo("Shariah Advisory Board");

    // IRIS+ mappings: a metric or a KPI, once per code; investors see visible ones with this and last year's values.
    String code = "PI" + (1000 + (int) (Math.random() * 8999));
    String impact = "{\"irisCode\":\"" + code + "\",\"name\":\"Permanent employees\",\"metricKey\":\"employees_total\",\"visibleToInvestors\":true}";
    send(post("/v1/investor/impact-metrics"), director, impact.replace(code, "PI-1")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.irisCode").exists());
    send(post("/v1/investor/impact-metrics"), director, impact.replace("\"metricKey\":\"employees_total\"", "\"metricKey\":\"employees_total\",\"kpiId\":\""
        + kpi.path("id").asText() + "\"")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.metricKey").exists());
    JsonNode m = data(send(post("/v1/investor/impact-metrics"), director, impact).andExpect(status().isCreated()));
    assertThat(m.path("sourceName").asText()).isEqualTo("Total employees");
    send(post("/v1/investor/impact-metrics"), director, impact).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.irisCode").exists());
    assertThat(contains(read("/v1/investor/impact-metrics", investor), m.path("id").asText())).isTrue();
    send(put("/v1/investor/impact-metrics/" + m.path("id").asText()), director, impact.replace("true", "false").replace("}", ",\"version\":"
        + m.path("version").asLong() + "}")).andExpect(status().isOk());
    assertThat(contains(read("/v1/investor/impact-metrics", investor), m.path("id").asText())).isFalse();
  }

  @Test
  void onlyInvestorRolesReachThePortal() throws Exception {
    for (String role : List.of("ESG_MANAGER", "ESG_ANALYST", "AUDITOR", "DATA_COLLECTOR", "BOARD_MEMBER")) {
      mvc.perform(get("/v1/investor/dashboards").header("Authorization", bearerForRole(role))).andExpect(status().isForbidden());
    }
    JsonNode d = read("/v1/investor/dashboards", bearerForRole("ESG_DIRECTOR"));
    assertThat(d.path("manager").asBoolean()).isTrue();
    mvc.perform(get("/v1/investor/scorecards/preview").header("Authorization", bearerForRole("INVESTOR_VIEWER"))).andExpect(status().isForbidden());
    mvc.perform(get("/v1/investor/people").header("Authorization", bearerForRole("INVESTOR_VIEWER"))).andExpect(status().isForbidden());
    // Tenant B's plan has no investor portal.
    mvc.perform(get("/v1/investor/dashboards").header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B))).andExpect(status().isForbidden());
  }

  private static void owner(String sql) throws SQLException {
    try (Connection c = PostgresTestDatabase.ownerConnection(); Statement st = c.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      st.execute(sql);
    }
  }

  static String ownerValue(String sql) throws SQLException {
    try (Connection c = PostgresTestDatabase.ownerConnection(); Statement st = c.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      ResultSet rs = st.executeQuery(sql);
      rs.next();
      return rs.getString(1);
    }
  }
}
