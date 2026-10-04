package com.esg.datahub;

import com.esg.datahub.DataHubDtos.DataSourceRequest;
import com.esg.datahub.DataHubDtos.DataSourceResponse;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data sources (TRD §4.3 data_sources): where a data point came from. Connection settings for API/ERP/IoT sources
 * arrive with their adapters (WP-41); this manages the register the data points reference.
 */
@Service
public class DataSourceService {

    private static final String SELECT = "select s.*, (select count(*) from data_hub.data_points p "
            + "where p.source_id = s.id and p.deleted_at is null) as point_count, o.first_name || ' ' || o.last_name as owner_name "
            + "from data_hub.data_sources s left join auth.users o on o.id = s.owner_user_id ";

    private final JdbcTemplate jdbc;

    public DataSourceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DataSourceResponse> list(boolean activeOnly) {
        return jdbc.query(SELECT + "where s.deleted_at is null and (? = false or s.is_active) order by lower(s.name)",
                (rs, i) -> response(rs), activeOnly);
    }

    public DataSourceResponse get(UUID id) {
        return jdbc.query(SELECT + "where s.id = ? and s.deleted_at is null", (rs, i) -> response(rs), id).stream()
                .findFirst().orElseThrow(() -> ApiException.notFound("Data source"));
    }

    @Transactional
    public DataSourceResponse create(DataSourceRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into data_hub.data_sources (id, name, source_type, description, is_active, owner_user_id) "
                    + "values (?, ?, ?, ?, ?, ?)", id, r.name().trim(), r.sourceType(), blank(r.description()), r.active(), r.ownerUserId());
        } catch (DuplicateKeyException e) {
            throw FieldValidationException.of("name", "A data source with this name already exists");
        }
        return get(id);
    }

    @Transactional
    public DataSourceResponse update(UUID id, DataSourceRequest r) {
        get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        check(r);
        int updated;
        try {
            updated = jdbc.update("update data_hub.data_sources set name = ?, source_type = ?, description = ?, is_active = ?, "
                    + "owner_user_id = ?, version = version + 1 where id = ? and version = ? and deleted_at is null", r.name().trim(),
                    r.sourceType(), blank(r.description()), r.active(), r.ownerUserId(), id, r.version());
        } catch (DuplicateKeyException e) {
            throw FieldValidationException.of("name", "A data source with this name already exists");
        }
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This data source was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    /**
     * Phase 3 (8): a source is a system, not a person. A name that is an active colleague's full name is refused with a
     * pointer to the owner field; the owner must be an active user of the tenant.
     */
    private void check(DataSourceRequest r) {
        java.util.Map<String, String> errors = new java.util.LinkedHashMap<>();
        Integer people = jdbc.queryForObject("select count(*) from auth.users where deleted_at is null and is_active "
                + "and lower(first_name || ' ' || last_name) = lower(?)", Integer.class, r.name().trim());
        if (people != null && people > 0) {
            errors.put("name", "This is a person's name. Name the system (e.g. \"Generator meter #2\") and choose the person as the owner");
        }
        if (r.ownerUserId() != null && !isAssignableOwner(r.ownerUserId())) {
            errors.put("ownerUserId", "is not an active user in your organisation");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    /** An active, non-platform user of the current tenant (RLS limits the lookup to the tenant). */
    public boolean isAssignableOwner(UUID userId) {
        Integer n = jdbc.queryForObject("select count(*) from auth.users where id = ? and deleted_at is null and is_active "
                + "and not auth.is_platform_user(id)", Integer.class, userId);
        return n != null && n > 0;
    }

    public record Person(UUID id, String name, String email) {}

    /** Phase 3 (8): people who can be responsible for a source or a data point (active, non-platform users). */
    public List<Person> people() {
        return jdbc.query("select id, first_name || ' ' || last_name, email from auth.users where is_active and deleted_at is null "
                        + "and not auth.is_platform_user(id) order by first_name, last_name",
                (rs, i) -> new Person(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)));
    }

    /** The person responsible for a source system, or null. */
    public UUID ownerOf(UUID sourceId) {
        return jdbc.query("select owner_user_id from data_hub.data_sources where id = ? and deleted_at is null",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, sourceId);
    }

    /** True if the source exists, isn't deleted and is active (data points may only reference active sources). */
    public boolean isActive(UUID id) {
        Boolean active = jdbc.query("select is_active from data_hub.data_sources where id = ? and deleted_at is null",
                rs -> rs.next() ? rs.getBoolean(1) : null, id);
        return Boolean.TRUE.equals(active);
    }

    private static DataSourceResponse response(ResultSet rs) throws SQLException {
        Timestamp sync = rs.getTimestamp("last_sync_at");
        return new DataSourceResponse(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("source_type"),
                rs.getString("description"), rs.getBoolean("is_active"), sync == null ? null : sync.toInstant(),
                rs.getLong("point_count"), rs.getLong("version"), rs.getObject("owner_user_id", UUID.class), rs.getString("owner_name"));
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
