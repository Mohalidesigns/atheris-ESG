package com.esg.datahub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/** Bulk import (WP-20, BRD-5.2.1-08, journey 4): upload → map → background processing → per-row errors. */
class ImportApiTest extends ApiTestSupport {

  @Autowired private ObjectMapper json;

  private String collector() {
    return bearerForRole("DATA_COLLECTOR"); // scoped to A Site 1
  }

  private JsonNode upload(String who, MockMultipartFile file) throws Exception {
    String body = mvc.perform(multipart("/v1/data/imports").file(file).header("Authorization", who))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(body).path("data");
  }

  private JsonNode start(String who, JsonNode job, String body) throws Exception {
    String response = mvc.perform(post("/v1/data/imports/" + job.path("id").asText() + "/start").header("Authorization", who)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
    return json.readTree(response).path("data");
  }

  /** Polls until the background job finishes (≤ 20 s). */
  private JsonNode await(String who, JsonNode job) throws Exception {
    for (int i = 0; i < 200; i++) {
      JsonNode current = json.readTree(mvc.perform(get("/v1/data/imports/" + job.path("id").asText()).header("Authorization", who))
          .andReturn().getResponse().getContentAsString()).path("data");
      if (!"processing".equals(current.path("status").asText())) {
        return current;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("import didn't finish");
  }

  @Test
  void csvImportCreatesDraftsAndReportsRowErrors() throws Exception {
    String csv = "Site,Metric,Value,Start,End\n"
        + "A Site 1,diesel_litres,\"1,250.5\",2026-08-01,2026-08-31\n"         // ok (thousands separator)
        + "A Site 1,Electricity consumed,9800,01/08/2026,31/08/2026\n"       // ok (metric by name, d/M/yyyy)
        + "A Site 1,unknown_metric,5,2026-08-01,2026-08-31\n"                 // unknown metric
        + "A Site 1,diesel_litres,lots,2026-09-01,2026-09-30\n"               // not a number
        + "A HQ,diesel_litres,10,2026-08-01,2026-08-31\n"                     // outside the collector's org scope
        + "A Site 1,employees_total,10.5,2026-08-01,2026-08-31\n"             // rule: whole number
        + "\n";                                                               // blank row ignored
    JsonNode job = upload(collector(), new MockMultipartFile("file", "august.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
    assertThat(job.path("status").asText()).isEqualTo("uploaded");
    assertThat(job.path("totalRows").asInt()).isEqualTo(6);
    assertThat(job.path("columns").toString()).isEqualTo("[\"Site\",\"Metric\",\"Value\",\"Start\",\"End\"]");
    // Header synonyms suggest the mapping.
    assertThat(job.path("suggestedMapping").path("organisation").asText()).isEqualTo("Site");
    assertThat(job.path("suggestedMapping").path("periodStart").asText()).isEqualTo("Start");

    JsonNode done = await(collector(), start(collector(), job, "{\"version\":" + job.path("version").asLong() + ",\"mapping\":"
        + "{\"organisation\":\"Site\",\"metric\":\"Metric\",\"value\":\"Value\",\"periodStart\":\"Start\",\"periodEnd\":\"End\"}}"));
    assertThat(done.path("status").asText()).isEqualTo("completed");
    assertThat(done.path("processedRows").asInt()).isEqualTo(6);
    assertThat(done.path("succeededRows").asInt()).isEqualTo(2);
    assertThat(done.path("failedRows").asInt()).isEqualTo(4);
    JsonNode errors = done.path("rowErrors");
    assertThat(errors.findValuesAsText("row")).containsExactly("4", "5", "6", "7");
    assertThat(errors.get(0).path("message").asText()).contains("Unknown metric 'unknown_metric'");
    assertThat(errors.get(0).path("column").asText()).isEqualTo("Metric");
    assertThat(errors.get(1).path("message").asText()).contains("isn't a number");
    assertThat(errors.get(2).path("message").asText()).contains("access to this organisation");
    assertThat(errors.get(3).path("field").asText()).isEqualTo("numericValue");
    assertThat(errors.get(3).path("column").asText()).isEqualTo("Value");

    // The good rows are drafts linked to the job, with the converted number.
    mvc.perform(get("/v1/data/points?importJobId=" + job.path("id").asText()).header("Authorization", collector()))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content[?(@.metricKey == 'diesel_litres')].numericValue").value(1250.5))
        .andExpect(jsonPath("$.data.content[0].validationStatus").value("draft"));
  }

  @Test
  void excelImportUsesDefaultsForEveryRow() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Data");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("KPI");
      header.createCell(1).setCellValue("Amount");
      header.createCell(2).setCellValue("Note");
      Row r1 = sheet.createRow(1);
      r1.createCell(0).setCellValue("lpg_kg");
      r1.createCell(1).setCellValue(77.25);
      r1.createCell(2).setCellValue("Canteen");
      Row r2 = sheet.createRow(2);
      r2.createCell(0).setCellValue("water_withdrawal_m3");
      r2.createCell(1).setCellValue(1200);
      CellStyle unused = wb.createCellStyle(); // workbook with styles, as Excel writes them
      unused.setWrapText(true);
      wb.write(out);
    }
    String director = bearerForRole("ESG_DIRECTOR");
    JsonNode job = upload(director, new MockMultipartFile("file", "q3.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray()));
    assertThat(job.path("fileFormat").asText()).isEqualTo("xlsx");
    LocalDate start = LocalDate.of(2026, 9, 1);
    JsonNode done = await(director, start(director, job, "{\"version\":" + job.path("version").asLong() + ",\"mapping\":"
        + "{\"metric\":\"KPI\",\"value\":\"Amount\",\"subCategory\":\"Note\"},\"defaultOrgId\":\"" + ORG_HQ + "\","
        + "\"defaultPeriodStart\":\"" + start + "\",\"defaultPeriodEnd\":\"" + start.plusDays(29) + "\","
        + "\"sourceId\":\"a0000000-0000-4000-8000-0000000000c1\"}"));
    assertThat(done.path("succeededRows").asInt()).isEqualTo(2);
    assertThat(done.path("failedRows").asInt()).isZero();
    mvc.perform(get("/v1/data/points?importJobId=" + job.path("id").asText() + "&metricKey=lpg_kg").header("Authorization", director))
        .andExpect(jsonPath("$.data.content[0].orgId").value(ORG_HQ))
        .andExpect(jsonPath("$.data.content[0].subCategory").value("Canteen"))
        .andExpect(jsonPath("$.data.content[0].sourceName").value("Manual"))
        .andExpect(jsonPath("$.data.content[0].reportingPeriodEnd").value("2026-09-30"));
  }

  @Test
  void mappingIsValidated() throws Exception {
    JsonNode job = upload(collector(), new MockMultipartFile("file", "x.csv", "text/csv",
        "Metric,Value\ndiesel_litres,1\n".getBytes(StandardCharsets.UTF_8)));
    String v = "\"version\":" + job.path("version").asLong();
    mvc.perform(post("/v1/data/imports/" + job.path("id").asText() + "/start").header("Authorization", collector())
            .contentType(MediaType.APPLICATION_JSON).content("{" + v + ",\"mapping\":{\"metric\":\"Metric\",\"value\":\"Nope\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['mapping.value']").exists())
        .andExpect(jsonPath("$.errors['mapping.organisation']").exists())
        .andExpect(jsonPath("$.errors['mapping.periodStart']").exists())
        .andExpect(jsonPath("$.errors['mapping.periodEnd']").exists());
    mvc.perform(post("/v1/data/imports/" + job.path("id").asText() + "/start").header("Authorization", collector())
            .contentType(MediaType.APPLICATION_JSON).content("{" + v + ",\"mapping\":{\"metric\":\"Metric\",\"value\":\"Value\"},"
                + "\"defaultOrgId\":\"" + ORG_SITE_1 + "\",\"defaultPeriodStart\":\"2026-05-02\",\"defaultPeriodEnd\":\"2026-05-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.defaultPeriodEnd").exists());
  }

  @Test
  void uploadRejectsWrongTypesEmptyFilesAndReadOnlyRoles() throws Exception {
    mvc.perform(multipart("/v1/data/imports").file(new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[] {1}))
            .header("Authorization", collector()))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.file").exists());
    mvc.perform(multipart("/v1/data/imports").file(new MockMultipartFile("file", "x.csv", "text/csv",
                "Metric,Value\n".getBytes(StandardCharsets.UTF_8)))
            .header("Authorization", collector()))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.file").value("The file has no data rows"));
    mvc.perform(multipart("/v1/data/imports").file(new MockMultipartFile("file", "x.csv", "text/csv",
                "Metric,Value\ndiesel_litres,1\n".getBytes(StandardCharsets.UTF_8)))
            .header("Authorization", bearerForRole("AUDITOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  void templateListsTheImportColumns() throws Exception {
    mvc.perform(get("/v1/data/imports/template").header("Authorization", collector()))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.startsWith("organisation,metric,value,currency,period_start,period_end")));
  }
}
