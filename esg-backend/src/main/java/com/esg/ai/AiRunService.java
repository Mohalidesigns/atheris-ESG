package com.esg.ai;

import com.esg.ai.AiDtos.DismissRequest;
import com.esg.ai.AiDtos.Run;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AI runs (D38 (5)): what ran, on which engine, with which inputs, and what a person decided about it. */
@Service
public class AiRunService {

    static final Set<String> KINDS = Set.of("forecast", "isolation_forest", "extraction");
    private static final String SELECT = "select r.*, f.original_name as file_name, cu.first_name || ' ' || cu.last_name as created_by_name, "
            + "ru.first_name || ' ' || ru.last_name as reviewed_by_name from ai.runs r left join files.file_metadata f on f.id = r.file_id "
            + "left join auth.users cu on cu.id = r.created_by left join auth.users ru on ru.id = r.reviewed_by ";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AiRunService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    String write(Object o) {
        try {
            return o == null ? null : json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    UUID start(String kind, Object parameters, Object inputSummary, UUID fileId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into ai.runs (id, kind, parameters, input_summary, file_id) values (?, ?, ?::jsonb, ?::jsonb, ?)", id, kind, write(parameters),
                write(inputSummary), fileId);
        return id;
    }

    void complete(UUID id, String engine, JsonNode output) {
        jdbc.update("update ai.runs set status = 'completed', engine = ?, output = ?::jsonb, completed_at = now(), version = version + 1 where id = ?",
                engine, write(output), id);
    }

    void fail(UUID id, String error) {
        String e = error == null || error.isBlank() ? "The AI service failed" : error;
        jdbc.update("update ai.runs set status = 'failed', error = ?, completed_at = now(), version = version + 1 where id = ?",
                e.length() > 1000 ? e.substring(0, 1000) : e, id);
    }

    public List<Run> list(String kind) {
        if (kind != null && !KINDS.contains(kind)) {
            throw FieldValidationException.of("kind", "must be forecast, isolation_forest or extraction");
        }
        return jdbc.query(SELECT + "where r.deleted_at is null" + (kind == null ? "" : " and r.kind = ?") + " order by r.created_at desc limit 100",
                (rs, i) -> run(rs), kind == null ? new Object[0] : new Object[] {kind});
    }

    public Run get(UUID id) {
        return jdbc.query(SELECT + "where r.id = ? and r.deleted_at is null", (rs, i) -> run(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("AI run"));
    }

    Run get(UUID id, String kind) {
        Run r = get(id);
        if (!r.kind().equals(kind)) {
            throw ApiException.notFound("AI run");
        }
        return r;
    }

    /** Overriding an AI output: a person sets it aside with a reason; it stays on record. */
    @Transactional
    public Run dismiss(UUID id, String kind, DismissRequest r) {
        Run run = get(id, kind);
        if (!"completed".equals(run.status()) || "dismissed".equals(run.reviewStatus())) {
            throw ApiException.conflict("ESG-409-STATE", "Only a completed, undismissed result can be dismissed.");
        }
        if (jdbc.update("update ai.runs set review_status = 'dismissed', review_note = ?, reviewed_by = ?, reviewed_at = now(), version = version + 1 "
                + "where id = ? and version = ?", r.note().trim(), CurrentUser.access().userId(), id, r.version()) == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This result was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    private Run run(ResultSet rs) throws SQLException {
        return new Run(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("status"), rs.getString("engine"), node(rs.getString("parameters")),
                node(rs.getString("input_summary")), node(rs.getString("output")), rs.getString("error"), rs.getObject("file_id", UUID.class),
                rs.getString("file_name"), rs.getString("review_status"), rs.getString("review_note"), rs.getString("reviewed_by_name"),
                instant(rs.getTimestamp("reviewed_at")), rs.getString("created_by_name"), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("completed_at")), true, rs.getLong("version"));
    }

    private JsonNode node(String s) {
        try {
            return s == null ? null : json.readTree(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
