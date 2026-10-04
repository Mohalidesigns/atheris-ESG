package com.esg.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.image.BufferedImage;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * WP-42 M12 Training through the API: journey 25 (a course is built and published → assigned → the learner works
 * through the modules and the quiz → a certificate with a QR code → anyone verifies it publicly → it is revoked),
 * prerequisites, classroom attendance under the period lock, draft-only course content, competency gaps and access.
 */
class TrainingApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;

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

  private static String course(String code, String delivery, String prerequisites, String modules, String questions) {
    return "{\"code\":\"" + code + "\",\"title\":\"Course " + code + "\",\"description\":\"What ESG means here\",\"category\":\"esg_fundamentals\","
        + "\"delivery\":\"" + delivery + "\",\"durationHours\":1.5,\"passingScore\":50,\"certificateValidityMonths\":12,\"prerequisiteIds\":[" + prerequisites
        + "],\"modules\":[" + modules + "],\"questions\":[" + questions + "]}";
  }

  private static final String MODULES = "{\"title\":\"Why ESG\",\"kind\":\"text\",\"body\":\"Environmental, social and governance.\",\"minutes\":20},"
      + "{\"title\":\"Talk\",\"kind\":\"video\",\"url\":\"https://videos.example.org/esg-intro\",\"minutes\":15}";
  private static final String QUESTIONS = "{\"question\":\"What does the G stand for?\",\"options\":[\"Green\",\"Governance\",\"Growth\"],\"correctIndex\":1},"
      + "{\"question\":\"Scope 1 emissions are\",\"options\":[\"Direct\",\"Purchased electricity\"],\"correctIndex\":0}";

  private JsonNode publish(String token, JsonNode c) throws Exception {
    return data(send(post("/v1/training/courses/" + c.path("id").asText() + "/actions"), token, "{\"action\":\"publish\",\"version\":"
        + c.path("version").asLong() + "}").andExpect(status().isOk()));
  }

  private JsonNode myEnrollment(String token, String courseId) throws Exception {
    for (JsonNode e : read("/v1/training/progress", token).path("enrollments")) {
      if (e.path("courseId").asText().equals(courseId)) {
        return e;
      }
    }
    throw new AssertionError("not enrolled in " + courseId);
  }

  @Test
  void journey25ACourseIsCompletedAndItsCertificateVerifiedByAnyone() throws Exception {
    String admin = bearerForRole("TRAINING_ADMIN");
    String learner = bearerForRole("ESG_ANALYST");
    String tag = Long.toString(System.nanoTime(), 36).toUpperCase();

    // Built as a draft: module content by kind, answers among the options, a unique code.
    send(post("/v1/training/courses"), admin, course("ESG-" + tag, "online", "", "{\"title\":\"Empty\",\"kind\":\"text\",\"minutes\":5}", ""))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors['modules[0].body']").exists());
    send(post("/v1/training/courses"), admin, course("ESG-" + tag, "online", "", MODULES,
        "{\"question\":\"Q\",\"options\":[\"A\",\"B\"],\"correctIndex\":2}")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['questions[0].correctIndex']").exists());
    send(post("/v1/training/courses"), admin, course("esg " + tag, "online", "", MODULES, QUESTIONS)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.code").exists());
    JsonNode c = data(send(post("/v1/training/courses"), admin, course("ESG-" + tag, "online", "", MODULES, QUESTIONS)).andExpect(status().isCreated()));
    String courseId = c.path("id").asText();
    assertThat(c.path("status").asText()).isEqualTo("draft");
    assertThat(c.path("modules")).hasSize(2);
    assertThat(c.path("questions").get(0).path("correctIndex").asInt()).isEqualTo(1);
    send(post("/v1/training/courses"), admin, course("ESG-" + tag, "online", "", MODULES, QUESTIONS)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.code").value("is already used by another course"));
    // Learners don't see drafts.
    mvc.perform(get("/v1/training/courses/" + courseId).header("Authorization", learner)).andExpect(status().isNotFound());

    c = publish(admin, c);
    assertThat(c.path("status").asText()).isEqualTo("published");
    assertThat(c.path("actions").toString()).contains("retire").contains("copy");
    // Published content can't change, not even past the API.
    send(put("/v1/training/courses/" + courseId), admin, course("ESG-" + tag, "online", "", MODULES, QUESTIONS).replace("}]}", "}],\"version\":"
        + c.path("version").asLong() + "}")).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-STATE"));
    assertThatThrownBy(() -> owner("insert into training.course_modules (course_id, sort_order, title, kind, body, minutes) values ('" + courseId
        + "', 9, 'Sneaked in', 'text', 'x', 5)")).isInstanceOf(SQLException.class).hasMessageContaining("draft");

    // The learner sees it without the answers; a second course needs this one first.
    JsonNode seen = read("/v1/training/courses/" + courseId, learner);
    assertThat(seen.path("questions").get(0).path("correctIndex").isMissingNode() || seen.path("questions").get(0).path("correctIndex").isNull()).isTrue();
    assertThat(seen.path("actions")).isEmpty();
    JsonNode advanced = publish(admin, data(send(post("/v1/training/courses"), admin, course("ADV-" + tag, "online", "\"" + courseId + "\"",
        "{\"title\":\"Advanced\",\"kind\":\"text\",\"body\":\"More.\",\"minutes\":10}", "")).andExpect(status().isCreated())));
    send(post("/v1/training/courses/" + advanced.path("id").asText() + "/enrol"), learner, "").andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ESG-409-STATE"));

    // Assigned with a due date (not in the past); a second assignment counts as already enrolled.
    String learnerId = roleUserId("ESG_ANALYST");
    send(post("/v1/training/enrollments"), admin, "{\"courseId\":\"" + courseId + "\",\"userIds\":[\"" + learnerId + "\"],\"dueOn\":\""
        + TODAY.minusDays(1) + "\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.dueOn").exists());
    send(post("/v1/training/enrollments"), admin, "{\"courseId\":\"" + courseId + "\",\"userIds\":[\"" + learnerId + "\"],\"dueOn\":\""
        + TODAY.plusDays(30) + "\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.data.enrolled").value(1));
    send(post("/v1/training/enrollments"), admin, "{\"courseId\":\"" + courseId + "\",\"userIds\":[\"" + learnerId + "\"]}")
        .andExpect(status().isCreated()).andExpect(jsonPath("$.data.alreadyEnrolled").value(1));
    assertThat(read("/v1/notifications?unreadOnly=true", learner).toString()).contains("Course ESG-" + tag);

    // The quiz waits for the modules; the learner works through them.
    JsonNode e = myEnrollment(learner, courseId);
    assertThat(e.path("source").asText()).isEqualTo("assigned");
    assertThat(e.path("modulesTotal").asInt()).isEqualTo(2);
    String eid = e.path("id").asText();
    send(post("/v1/training/enrollments/" + eid + "/quiz"), learner, "{\"answers\":[1,0],\"version\":" + e.path("version").asLong() + "}")
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-STATE"));
    // Only the learner progresses their own enrolment.
    mvc.perform(post("/v1/training/enrollments/" + eid + "/modules/" + seen.path("modules").get(0).path("id").asText() + "/done")
        .param("version", e.path("version").asText()).header("Authorization", admin)).andExpect(status().isForbidden());
    for (JsonNode m : seen.path("modules")) {
      e = data(mvc.perform(post("/v1/training/enrollments/" + eid + "/modules/" + m.path("id").asText() + "/done").param("version", e.path("version").asText())
          .header("Authorization", learner)).andExpect(status().isOk()));
    }
    assertThat(e.path("status").asText()).isEqualTo("in_progress");
    assertThat(e.path("modulesDone")).hasSize(2);

    // A failed attempt (0%), then a pass (100%): best score kept, completion today with the course's hours.
    send(post("/v1/training/enrollments/" + eid + "/quiz"), learner, "{\"answers\":[1],\"version\":" + e.path("version").asLong() + "}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.answers").exists());
    e = data(send(post("/v1/training/enrollments/" + eid + "/quiz"), learner, "{\"answers\":[0,1],\"version\":" + e.path("version").asLong() + "}")
        .andExpect(status().isOk()));
    assertThat(e.path("status").asText()).isEqualTo("failed");
    assertThat(e.path("bestScore").asInt()).isZero();
    e = data(send(post("/v1/training/enrollments/" + eid + "/quiz"), learner, "{\"answers\":[1,0],\"version\":" + e.path("version").asLong() + "}")
        .andExpect(status().isOk()));
    assertThat(e.path("status").asText()).isEqualTo("passed");
    assertThat(e.path("attempts").asInt()).isEqualTo(2);
    assertThat(e.path("bestScore").asInt()).isEqualTo(100);
    assertThat(e.path("completedOn").asText()).isEqualTo(TODAY.toString());
    assertThat(e.path("hours").decimalValue()).isEqualByComparingTo("1.5");
    JsonNode cert = e.path("certificate");
    String code = cert.path("code").asText();
    assertThat(code).matches("^[A-HJ-NP-Z2-9]{10}$");
    assertThat(cert.path("status").asText()).isEqualTo("valid");
    assertThat(cert.path("expiresOn").asText()).isEqualTo(TODAY.plusMonths(12).toString());
    assertThat(cert.path("holderName").asText()).isEqualTo("Esg Analyst Fixture");
    assertThat(cert.path("verifyUrl").asText()).endsWith("/verify-certificate/" + code);

    // The certificate PDF names the holder and course and carries a QR code to the verification page.
    byte[] pdf = mvc.perform(get("/v1/training/certificates/" + cert.path("id").asText() + "/pdf").header("Authorization", learner))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      String text = new PDFTextStripper().getText(doc);
      assertThat(text).contains("Esg Analyst Fixture").contains("Course ESG-" + tag).contains(code).contains("with a score of 100%");
      assertThat(decodeQr(doc)).isEqualTo(cert.path("verifyUrl").asText());
    }
    // Other learners can't fetch it.
    mvc.perform(get("/v1/training/certificates/" + cert.path("id").asText() + "/pdf").header("Authorization", bearerForRole("DATA_COLLECTOR")))
        .andExpect(status().isNotFound());

    // Anyone verifies it with the code alone — no session; nothing beyond holder, course, organisation and dates.
    JsonNode v = data(mvc.perform(get("/v1/public/certificates/" + code.toLowerCase())).andExpect(status().isOk()));
    assertThat(v.path("status").asText()).isEqualTo("valid");
    assertThat(v.path("holderName").asText()).isEqualTo("Esg Analyst Fixture");
    assertThat(v.path("courseTitle").asText()).isEqualTo("Course ESG-" + tag);
    assertThat(v.path("organisation").asText()).isNotBlank();
    assertThat(v.has("score")).isFalse();
    mvc.perform(get("/v1/public/certificates/AAAAAAAAAA")).andExpect(status().isNotFound());
    mvc.perform(get("/v1/public/certificates/not-a-code")).andExpect(status().isNotFound());

    // Passing unlocks the advanced course.
    send(post("/v1/training/courses/" + advanced.path("id").asText() + "/enrol"), learner, "").andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.source").value("self"));

    // Revoked with a reason (training:configure); verification then says so.
    send(post("/v1/training/certificates/" + cert.path("id").asText() + "/revoke"), bearerForRole("ESG_DIRECTOR"), "{\"note\":\"x\"}")
        .andExpect(status().isForbidden());
    send(post("/v1/training/certificates/" + cert.path("id").asText() + "/revoke"), admin, "{}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.note").exists());
    send(post("/v1/training/certificates/" + cert.path("id").asText() + "/revoke"), admin, "{\"note\":\"Quiz answers were shared\"}")
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("revoked"));
    assertThat(data(mvc.perform(get("/v1/public/certificates/" + code))).path("status").asText()).isEqualTo("revoked");
    // The course is still passed, but the competency now shows as a gap if a path requires it.
    String path = data(send(post("/v1/training/paths"), admin, "{\"name\":\"Analyst essentials " + tag + "\",\"targetRole\":\"ESG_ANALYST\","
        + "\"courseIds\":[\"" + courseId + "\"],\"status\":\"active\"}").andExpect(status().isCreated())).path("id").asText();
    assertThat(read("/v1/training/progress", learner).path("gaps").toString()).contains("\"courseId\":\"" + courseId + "\"").contains("\"state\":\"revoked\"");
    JsonNode team = read("/v1/training/team", admin);
    assertThat(team.toString()).contains("Esg Analyst Fixture");

    // The overview counts the hours; a retired course can't take new people.
    JsonNode overview = read("/v1/training/overview", admin);
    assertThat(overview.path("hours").decimalValue()).isGreaterThanOrEqualTo(new java.math.BigDecimal("1.5"));
    assertThat(overview.path("certificatesIssued").asInt()).isGreaterThanOrEqualTo(1);
    c = read("/v1/training/courses/" + courseId, admin);
    data(send(post("/v1/training/courses/" + courseId + "/actions"), admin, "{\"action\":\"retire\",\"version\":" + c.path("version").asLong() + "}")
        .andExpect(status().isOk()));
    send(post("/v1/training/enrollments"), admin, "{\"courseId\":\"" + courseId + "\",\"userIds\":[\"" + roleUserId("AUDITOR") + "\"]}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.courseId").exists());
    send(post("/v1/training/courses/" + courseId + "/actions"), admin, "{\"action\":\"copy\",\"version\":0}").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.code").value("ESG-" + tag + "-V1")).andExpect(jsonPath("$.data.status").value("draft"));
    assertThat(path).isNotBlank();

    // Links: the dashboard card for those who track training; report variables for the year.
    JsonNode dash = read("/v1/dashboard/executive", bearerForRole("ESG_DIRECTOR")).path("training");
    assertThat(dash.path("hours").decimalValue()).isGreaterThanOrEqualTo(new java.math.BigDecimal("1.5"));
    JsonNode managerDash = read("/v1/dashboard/executive", bearerForRole("ESG_MANAGER")).path("training");
    assertThat(managerDash.isMissingNode() || managerDash.isNull()).as("no training:update").isTrue();
    String manager = bearerForRole("ESG_MANAGER");
    String templateId = read("/v1/reports/templates", manager).get(0).path("id").asText();
    JsonNode report = data(send(post("/v1/reports"), manager, "{\"templateId\":\"" + templateId + "\",\"title\":\"Training " + tag
        + "\",\"reportingPeriodStart\":\"" + TODAY.getYear() + "-01-01\",\"reportingPeriodEnd\":\"" + TODAY.getYear() + "-12-31\"}").andExpect(status().isCreated()));
    String section = "{\"title\":\"People development\",\"type\":\"data\",\"content\":\"{{training:hours_total}} over {{training:certificates_issued}} "
        + "certificates; completion {{training:completion_rate}} {{training:nonsense}}\"}";
    send(put("/v1/reports/" + report.path("id").asText() + "/sections"), manager, "{\"version\":" + report.path("version").asLong() + ",\"sections\":["
        + section + "]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors['sections[0].content']").value("Unknown variable: {{training:nonsense}}"));
    report = data(send(put("/v1/reports/" + report.path("id").asText() + "/sections"), manager, "{\"version\":" + report.path("version").asLong()
        + ",\"sections\":[" + section.replace(" {{training:nonsense}}", "") + "]}").andExpect(status().isOk()));
    JsonNode snapshot = report.path("snapshot");
    assertThat(snapshot.path("training:hours_total").path("status").asText()).isEqualTo("ok");
    assertThat(snapshot.path("training:hours_total").path("display").asText()).endsWith(" hours");
    assertThat(snapshot.path("training:certificates_issued").path("status").asText()).isEqualTo("ok");
    assertThat(snapshot.path("training:completion_rate").path("display").asText()).endsWith("%");
  }

  @Test
  void classroomAttendanceIsRecordedByTheTrainingTeamAndRespectsLockedPeriods() throws Exception {
    String admin = bearerForRole("TRAINING_ADMIN");
    String tag = Long.toString(System.nanoTime(), 36).toUpperCase();
    JsonNode c = publish(admin, data(send(post("/v1/training/courses"), admin, course("CLS-" + tag, "classroom", "",
        "{\"title\":\"Workshop handout\",\"kind\":\"text\",\"body\":\"Agenda\",\"minutes\":240}", "")).andExpect(status().isCreated())));
    String collector = roleUserId("DATA_COLLECTOR");
    send(post("/v1/training/enrollments"), admin, "{\"courseId\":\"" + c.path("id").asText() + "\",\"userIds\":[\"" + collector + "\"]}")
        .andExpect(status().isCreated());
    JsonNode e = myEnrollment(bearerForRole("DATA_COLLECTOR"), c.path("id").asText());
    String eid = e.path("id").asText();
    // The learner can't complete a classroom course themselves, nor record attendance.
    mvc.perform(post("/v1/training/enrollments/" + eid + "/modules/" + read("/v1/training/courses/" + c.path("id").asText(), admin).path("modules").get(0)
        .path("id").asText() + "/done").param("version", e.path("version").asText()).header("Authorization", bearerForRole("DATA_COLLECTOR")))
        .andExpect(status().isConflict());
    String attendance = "{\"completedOn\":\"%s\",\"hours\":%s,\"passed\":true,\"score\":%s,\"version\":" + e.path("version").asLong() + "}";
    send(post("/v1/training/enrollments/" + eid + "/attendance"), bearerForRole("DATA_COLLECTOR"), attendance.formatted(TODAY, "4", "80"))
        .andExpect(status().isForbidden());
    send(post("/v1/training/enrollments/" + eid + "/attendance"), admin, attendance.formatted(TODAY.plusDays(1), "4", "80"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.completedOn").exists());
    send(post("/v1/training/enrollments/" + eid + "/attendance"), admin, attendance.formatted(TODAY, "0", "80"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.hours").exists());
    send(post("/v1/training/enrollments/" + eid + "/attendance"), admin, attendance.formatted(TODAY, "4", "120"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.score").exists());
    // FY2020 is locked in the fixture: hours can't be credited to it.
    send(post("/v1/training/enrollments/" + eid + "/attendance"), admin, attendance.formatted("2020-06-01", "4", "80"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ESG-409-LOCKED"));
    e = data(send(post("/v1/training/enrollments/" + eid + "/attendance"), admin, attendance.formatted(TODAY, "3.5", "80")).andExpect(status().isOk()));
    assertThat(e.path("status").asText()).isEqualTo("passed");
    assertThat(e.path("hours").decimalValue()).isEqualByComparingTo("3.5");
    assertThat(e.path("certificate").path("score").asInt()).isEqualTo(80);
    // A passed course stays on record.
    mvc.perform(post("/v1/training/enrollments/" + eid + "/withdraw").param("version", e.path("version").asText()).header("Authorization", admin))
        .andExpect(status().isConflict());
    // A stale version is refused.
    send(post("/v1/training/enrollments/" + eid + "/attendance"), admin, attendance.formatted(TODAY, "3.5", "80")).andExpect(status().isConflict());
  }

  @Test
  void pathsGapsAndAccess() throws Exception {
    String admin = bearerForRole("TRAINING_ADMIN");
    String tag = Long.toString(System.nanoTime(), 36).toUpperCase();
    JsonNode draft = data(send(post("/v1/training/courses"), admin, course("DRF-" + tag, "online", "", MODULES, "")).andExpect(status().isCreated()));
    // Paths take published courses and a working role.
    String path = "{\"name\":\"Path " + tag + "\",\"targetRole\":\"%s\",\"courseIds\":[\"%s\"],\"status\":\"active\"}";
    send(post("/v1/training/paths"), admin, path.formatted("ESG_ANALYST", draft.path("id").asText())).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.courseIds").exists());
    JsonNode published = publish(admin, draft);
    send(post("/v1/training/paths"), admin, path.formatted("INVESTOR_VIEWER", published.path("id").asText())).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.targetRole").exists());
    String pathId = data(send(post("/v1/training/paths"), admin, path.formatted("AUDITOR", published.path("id").asText())).andExpect(status().isCreated()))
        .path("id").asText();
    // The auditor has a gap until enrolled; assigning the path to its role enrols them.
    String auditor = bearerForRole("AUDITOR");
    assertThat(read("/v1/training/progress", auditor).path("gaps").toString()).contains("\"state\":\"not_enrolled\"").contains("Path " + tag);
    send(post("/v1/training/paths/assign"), admin, "{\"pathId\":\"" + pathId + "\"}").andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.enrolled").value(1));
    assertThat(myEnrollment(auditor, published.path("id").asText()).path("source").asText()).isEqualTo("path");

    // Everyone working reads training; only the training team manages it; directors assign and track.
    for (String role : List.of("ESG_DIRECTOR", "ESG_MANAGER", "ESG_ANALYST", "DATA_COLLECTOR", "AUDITOR", "CONSULTANT", "BOARD_MEMBER",
        "COMPLIANCE_OFFICER", "ETHICS_OFFICER", "TRAINING_ADMIN")) {
      mvc.perform(get("/v1/training/courses").header("Authorization", bearerForRole(role))).andExpect(status().isOk());
    }
    mvc.perform(get("/v1/training/courses").header("Authorization", bearerForRole("INVESTOR_VIEWER"))).andExpect(status().isForbidden());
    send(post("/v1/training/courses"), bearerForRole("ESG_DIRECTOR"), course("DIR-" + tag, "online", "", MODULES, "")).andExpect(status().isForbidden());
    mvc.perform(get("/v1/training/team").header("Authorization", bearerForRole("ESG_DIRECTOR"))).andExpect(status().isOk());
    mvc.perform(get("/v1/training/team").header("Authorization", bearerForRole("ESG_MANAGER"))).andExpect(status().isForbidden());
    mvc.perform(get("/v1/training/enrollments").header("Authorization", bearerForRole("ESG_ANALYST"))).andExpect(status().isForbidden());
    // Learners see published courses only.
    assertThat(read("/v1/training/courses", bearerForRole("ESG_ANALYST")).toString()).doesNotContain("\"status\":\"draft\"");
    // Another tenant's admin sees none of tenant A's courses.
    assertThat(read("/v1/training/courses", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B)).toString()).doesNotContain(tag);
    mvc.perform(get("/v1/training/progress")).andExpect(status().isUnauthorized());
  }

  /**
   * The QR code drawn at the bottom right of the certificate (CertificateService: 120 pt square, 60 pt from the right
   * and bottom edges), rendered at 300 dpi and decoded. Cropping and try-harder keep this independent of how the
   * platform's renderer anti-aliases the rest of the page (it failed on Linux CI decoding the whole page at 144 dpi).
   */
  private static String decodeQr(PDDocument doc) throws Exception {
    float dpi = 300f;
    float scale = dpi / 72f;
    BufferedImage page = new PDFRenderer(doc).renderImageWithDPI(0, dpi, org.apache.pdfbox.rendering.ImageType.GRAY);
    float pageWidth = doc.getPage(0).getMediaBox().getWidth();
    float pageHeight = doc.getPage(0).getMediaBox().getHeight();
    int x = Math.round((pageWidth - 60 - 120 - 20) * scale);
    int y = Math.round((pageHeight - 60 - 120 - 20) * scale);
    int size = Math.round(160 * scale);
    BufferedImage qr = page.getSubimage(x, y, Math.min(size, page.getWidth() - x), Math.min(size, page.getHeight() - y));
    int[] pixels = qr.getRGB(0, 0, qr.getWidth(), qr.getHeight(), null, 0, qr.getWidth());
    RGBLuminanceSource source = new RGBLuminanceSource(qr.getWidth(), qr.getHeight(), pixels);
    java.util.Map<com.google.zxing.DecodeHintType, Object> hints = java.util.Map.of(com.google.zxing.DecodeHintType.TRY_HARDER, Boolean.TRUE);
    try {
      return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source)), hints).getText();
    } catch (com.google.zxing.NotFoundException e) {
      return new QRCodeReader().decode(new BinaryBitmap(new com.google.zxing.common.GlobalHistogramBinarizer(source)), hints).getText();
    }
  }

  private static void owner(String... sql) throws SQLException {
    try (Connection c = PostgresTestDatabase.ownerConnection(); Statement st = c.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      for (String s : sql) {
        st.execute(s);
      }
    }
  }
}
