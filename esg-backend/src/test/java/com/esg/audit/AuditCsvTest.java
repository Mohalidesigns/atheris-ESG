package com.esg.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Phase 3 (6): CSV cells are quoted and can't run as spreadsheet formulas; changes read "field: before → after". */
class AuditCsvTest {

  @Test
  void cellsAreQuotedAndFormulaPrefixesNeutralised() {
    assertThat(AuditQueryService.cell("plain")).isEqualTo("\"plain\"");
    assertThat(AuditQueryService.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
    assertThat(AuditQueryService.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
    assertThat(AuditQueryService.cell("+1")).isEqualTo("\"'+1\"");
    assertThat(AuditQueryService.cell("-1")).isEqualTo("\"'-1\"");
    assertThat(AuditQueryService.cell("@SUM")).isEqualTo("\"'@SUM\"");
    assertThat(AuditQueryService.cell(null)).isEmpty();
  }

  @Test
  void changesShowBeforeAndAfterForUpdatesAndValuesOtherwise() throws Exception {
    ObjectMapper json = new ObjectMapper();
    AuditDtos.AuditEntry update = new AuditDtos.AuditEntry(UUID.randomUUID(), Instant.now(), null, null, "update", "data_hub.data_points",
        null, json.readTree("{\"validation_status\":\"draft\",\"numeric_value\":1}"),
        json.readTree("{\"validation_status\":\"submitted\",\"numeric_value\":2}"), null, null);
    assertThat(AuditQueryService.changes(update)).isEqualTo("numeric_value: 1 → 2; validation_status: draft → submitted");
    AuditDtos.AuditEntry insert = new AuditDtos.AuditEntry(UUID.randomUUID(), Instant.now(), null, null, "insert", "x", null, null,
        json.readTree("{\"name\":\"A\",\"note\":null}"), null, null);
    assertThat(AuditQueryService.changes(insert)).isEqualTo("name: A; note: —");
  }
}
