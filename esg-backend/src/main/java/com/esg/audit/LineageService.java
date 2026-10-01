package com.esg.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Records data lineage edges (TRD §6.6.2): where a value came from and what it fed into, e.g.
 * emission_factor → emission_record ("calculated_from"). Edges are immutable (V014).
 */
@Service
public class LineageService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public LineageService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(String sourceType, UUID sourceId, String targetType, UUID targetId, String relation,
                       Map<String, Object> details) {
        try {
            jdbc.update("insert into audit.data_lineage (source_type, source_id, target_type, target_id, relation, details) "
                            + "values (?, ?, ?, ?, ?, ?::jsonb)", sourceType, sourceId, targetType, targetId, relation,
                    details == null ? null : json.writeValueAsString(details));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Lineage details must be JSON-serialisable", e);
        }
    }
}
