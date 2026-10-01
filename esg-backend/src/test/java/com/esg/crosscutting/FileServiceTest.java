package com.esg.crosscutting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

/** File service (WP-15, TRD §6.6.4) on the filesystem adapter; the S3 adapter is covered by S3FileStorageTest. */
@TestPropertySource(properties = "esg.files.max-bytes=2048")
class FileServiceTest extends ApiTestSupport {

  private static final byte[] PDF_V1 = "%PDF-1.4 version one".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] PDF_V2 = "%PDF-1.7 version two".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

  @Autowired private ObjectMapper json;

  private String upload(String role, String name, byte[] bytes, int expectedStatus) throws Exception {
    return mvc.perform(multipart("/v1/files").file(new MockMultipartFile("file", name, "application/octet-stream", bytes))
            .param("resourceType", "data_point").param("resourceId", HQ_DATA_POINT)
            .header("Authorization", bearerForRole(role)))
        .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
  }

  @Test
  void uploadVersionDownloadRoundTripWithAccessLog() throws Exception {
    String id = json.readTree(upload("ESG_ANALYST", "evidence.pdf", PDF_V1, 201)).path("data").path("id").asText();
    String director = bearerForRole("ESG_DIRECTOR");

    mvc.perform(multipart("/v1/files/" + id + "/versions").file(new MockMultipartFile("file", "evidence-v2.pdf", "application/pdf", PDF_V2))
            .header("Authorization", director))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.currentVersion").value(2))
        .andExpect(jsonPath("$.data.versions.length()").value(2))
        .andExpect(jsonPath("$.data.versions[0].scanStatus").value("not_scanned"));

    byte[] latest = mvc.perform(get("/v1/files/" + id + "/content").header("Authorization", director))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andReturn().getResponse().getContentAsByteArray();
    assertThat(latest).isEqualTo(PDF_V2);
    byte[] first = mvc.perform(get("/v1/files/" + id + "/content?version=1").header("Authorization", director))
        .andReturn().getResponse().getContentAsByteArray();
    assertThat(first).isEqualTo(PDF_V1);

    mvc.perform(get("/v1/files/" + id + "/download-url").header("Authorization", director))
        .andExpect(jsonPath("$.data.direct").value(false))
        .andExpect(jsonPath("$.data.url").value("/api/v1/files/" + id + "/content?version=2"));

    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      ResultSet rs = st.executeQuery("select string_agg(action, ',' order by created_at) from files.file_access_log where file_id = '" + id + "'");
      rs.next();
      assertThat(rs.getString(1)).isEqualTo("upload,upload,download,download,download_url");
    }
  }

  @Test
  void typeAndContentAndSizeAreChecked() throws Exception {
    upload("ESG_ANALYST", "tool.exe", PDF_V1, 415);
    upload("ESG_ANALYST", "fake.pdf", PNG, 415);
    upload("ESG_ANALYST", "chart.png", PNG, 201);
    upload("ESG_ANALYST", "data.csv", "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8), 201);
    upload("ESG_ANALYST", "big.pdf", ("%PDF" + "x".repeat(3000)).getBytes(StandardCharsets.US_ASCII), 413);
  }

  @Test
  void permissionsAndTenantIsolation() throws Exception {
    String id = json.readTree(upload("ESG_DIRECTOR", "board-pack.pdf", PDF_V1, 201)).path("data").path("id").asText();
    upload("BOARD_MEMBER", "x.pdf", PDF_V1, 403);
    mvc.perform(get("/v1/files/" + id).header("Authorization", bearerForRole("BOARD_MEMBER"))).andExpect(status().isOk());
    mvc.perform(get("/v1/files/" + id).header("Authorization", bearerForRole("INVESTOR_VIEWER"))).andExpect(status().isForbidden());
    mvc.perform(get("/v1/files/" + id).header("Authorization", bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B)))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/v1/files/" + id).header("Authorization", bearerForRole("ESG_ANALYST"))).andExpect(status().isForbidden());
    mvc.perform(delete("/v1/files/" + id).header("Authorization", bearerForRole("ESG_DIRECTOR"))).andExpect(status().isOk());
    mvc.perform(get("/v1/files/" + id).header("Authorization", bearerForRole("ESG_DIRECTOR"))).andExpect(status().isNotFound());
  }
}
