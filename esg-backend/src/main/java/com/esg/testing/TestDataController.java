package com.esg.testing;

import com.esg.model.dto.ApiResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 3 (7): removes the current tenant's test-created rows (is_test, V025 auth.purge_test_data()). Exists only when
 * test-data marking is on (local and test profiles; TestDataPolicy refuses it in staging/prod). Soft deletes, so the
 * audit trail records each one; rows in a locked reporting period are skipped and counted.
 */
@RestController
@RequestMapping("/v1/testing")
@ConditionalOnProperty(name = "esg.testing.test-data-marking", havingValue = "true")
public class TestDataController {

    public record Purged(String entity, int purged, int skipped) {}

    private final JdbcTemplate jdbc;

    public TestDataController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @DeleteMapping("/test-data")
    @PreAuthorize("hasAuthority('tenant_settings:configure')")
    @Transactional
    public ResponseEntity<ApiResponse<List<Purged>>> purge() {
        List<Purged> result = jdbc.query("select entity, purged, skipped from auth.purge_test_data()",
                (rs, i) -> new Purged(rs.getString(1), rs.getInt(2), rs.getInt(3)));
        return ResponseEntity.ok(ApiResponse.ok(result, "Test data removed"));
    }
}
