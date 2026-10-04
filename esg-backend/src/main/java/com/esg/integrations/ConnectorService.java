package com.esg.integrations;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.integrations.IntegrationDtos.Connector;
import com.esg.integrations.IntegrationDtos.PlatformRequest;
import com.esg.integrations.IntegrationDtos.Request;
import com.esg.integrations.IntegrationDtos.ConnectorRequestBody;
import com.esg.integrations.IntegrationDtos.StatusChange;
import com.esg.notifications.EmailSender;
import com.esg.security.CurrentUser;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WP-46 (D44) — the integration line. Named ERP, accounting, HRIS and utility connectors are offered "on request": a
 * tenant asks for one with the details the platform team needs, the team reviews, schedules and activates it (the
 * connector itself is built or configured then, behind {@link ErpConnector}), and the tenant follows the status here.
 * Nothing is connected until a request is activated; "available" entries (API keys, file templates) need no set-up.
 */
@Service
public class ConnectorService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorService.class);
    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");
    static final List<String> OPEN = List.of("requested", "in_review", "scheduled", "active");

    private final JdbcTemplate jdbc;
    private final EmailSender email;
    private final String platformInbox;

    public ConnectorService(JdbcTemplate jdbc, EmailSender email, @Value("${esg.integrations.requests-email:}") String platformInbox) {
        this.jdbc = jdbc;
        this.email = email;
        this.platformInbox = platformInbox;
    }

    // ---------------------------------------------------------------- tenant side

    @Transactional(readOnly = true)
    public List<Connector> catalogue() {
        Map<String, Request> open = requests().stream().filter(r -> OPEN.contains(r.status()))
                .collect(Collectors.toMap(Request::connectorKey, Function.identity(), (a, b) -> a));
        return jdbc.query("select connector_key, name, category, method, description, data_provided, availability "
                        + "from reference.integration_connectors order by sort_order, name",
                (rs, i) -> new Connector(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), open.get(rs.getString(1))));
    }

    @Transactional(readOnly = true)
    public List<Request> requests() {
        return jdbc.query(SELECT + " order by r.created_at desc", (rs, i) -> request(rs));
    }

    @Transactional
    public Request create(ConnectorRequestBody body) {
        List<String> availability = jdbc.queryForList("select availability from reference.integration_connectors where connector_key = ?",
                String.class, body.connectorKey());
        if (availability.isEmpty()) {
            throw FieldValidationException.of("connectorKey", "is not a connector we offer");
        }
        if ("available".equals(availability.get(0))) {
            throw FieldValidationException.of("connectorKey", "is available now — no request is needed");
        }
        List<String> unknown = body.dataNeeded().stream().filter(d -> !IntegrationDtos.DATA_CATEGORIES.contains(d)).toList();
        if (!unknown.isEmpty()) {
            throw FieldValidationException.of("dataNeeded", "contains an unknown kind of data: " + String.join(", ", unknown));
        }
        Integer n = jdbc.queryForObject("select count(*) + 1 from integrations.integration_requests", Integer.class);
        String reference = "INT-" + LocalDate.now(LAGOS).getYear() + "-" + String.format("%04d", n);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update(con -> {
                var ps = con.prepareStatement("insert into integrations.integration_requests (id, reference, connector_key, system_details, "
                        + "data_needed, frequency, contact_name, contact_email, contact_phone, notes) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                ps.setObject(1, id);
                ps.setString(2, reference);
                ps.setString(3, body.connectorKey());
                ps.setString(4, body.systemDetails().trim());
                ps.setArray(5, con.createArrayOf("text", body.dataNeeded().stream().distinct().toArray()));
                ps.setString(6, body.frequency());
                ps.setString(7, body.contactName().trim());
                ps.setString(8, body.contactEmail().trim());
                ps.setString(9, blank(body.contactPhone()));
                ps.setString(10, blank(body.notes()));
                return ps;
            });
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("ESG-409-DUPLICATE", "There is already an open request for this connector");
        }
        Request created = get(id);
        tellPlatform(created);
        return created;
    }

    /** The tenant withdraws a request that hasn't been activated. */
    @Transactional
    public Request withdraw(UUID id, long version) {
        Request r = get(id);
        if (!List.of("requested", "in_review", "scheduled").contains(r.status())) {
            throw ApiException.conflict("ESG-409-STATE", "A request that is " + r.status() + " can't be withdrawn");
        }
        int n = jdbc.update("update integrations.integration_requests set status = 'withdrawn', status_changed_at = now(), "
                + "status_changed_by = ?, version = version + 1 where id = ? and version = ?", CurrentUser.access().userId().toString(), id, version);
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This request was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    @Transactional(readOnly = true)
    public Request get(UUID id) {
        return jdbc.query(SELECT + " where r.id = ?", (rs, i) -> request(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Integration request"));
    }

    // ---------------------------------------------------------------- platform side (all tenants; tenants:read / tenants:update)

    @Transactional(readOnly = true)
    public List<PlatformRequest> platformQueue() {
        return jdbc.query("select * from integrations.platform_requests()", (rs, i) -> new PlatformRequest(rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class), rs.getString("tenant_name"), rs.getString("reference"), rs.getString("connector_key"),
                rs.getString("connector_name"), rs.getString("system_details"), list(rs.getArray("data_needed")), rs.getString("frequency"),
                rs.getString("contact_name"), rs.getString("contact_email"), rs.getString("contact_phone"), rs.getString("notes"),
                rs.getString("status"), rs.getString("status_note"), instant(rs.getTimestamp("status_changed_at")),
                instant(rs.getTimestamp("created_at")), rs.getLong("version")));
    }

    /** The platform team moves a request on: in review → scheduled → active (the connector is live), or declined. */
    @Transactional
    public PlatformRequest setStatus(UUID id, StatusChange change) {
        if ("declined".equals(change.status()) && (change.note() == null || change.note().isBlank())) {
            throw FieldValidationException.of("note", "Say why the request is declined");
        }
        String by = jdbc.queryForObject("select first_name || ' ' || last_name from auth.users where id = ?", String.class,
                CurrentUser.access().userId());
        Long version;
        try {
            version = jdbc.queryForObject("select integrations.set_request_status(?, ?, ?, ?, ?)", Long.class, id, change.status(),
                    blank(change.note()), "Atheris platform team (" + by + ")", change.version());
        } catch (DataAccessException e) {
            if (String.valueOf(e.getMessage()).contains("request changed or withdrawn")) {
                throw ApiException.conflict("ESG-409-VERSION", "This request was changed or withdrawn. Reload and try again.");
            }
            throw e;
        }
        if (version == null) {
            throw ApiException.notFound("Integration request");
        }
        PlatformRequest updated = platformQueue().stream().filter(r -> r.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Integration request"));
        tellRequester(updated);
        return updated;
    }

    // ---------------------------------------------------------------- helpers

    private static final String SELECT = "select r.id, r.reference, r.connector_key, c.name as connector_name, r.system_details, r.data_needed, "
            + "r.frequency, r.contact_name, r.contact_email, r.contact_phone, r.notes, r.status, r.status_note, r.status_changed_at, "
            + "r.created_at, u.first_name || ' ' || u.last_name as created_by_name, r.version "
            + "from integrations.integration_requests r join reference.integration_connectors c on c.connector_key = r.connector_key "
            + "left join auth.users u on u.id = r.created_by";

    private static Request request(ResultSet rs) throws SQLException {
        return new Request(rs.getObject("id", UUID.class), rs.getString("reference"), rs.getString("connector_key"), rs.getString("connector_name"),
                rs.getString("system_details"), list(rs.getArray("data_needed")), rs.getString("frequency"), rs.getString("contact_name"),
                rs.getString("contact_email"), rs.getString("contact_phone"), rs.getString("notes"), rs.getString("status"),
                rs.getString("status_note"), instant(rs.getTimestamp("status_changed_at")), instant(rs.getTimestamp("created_at")),
                rs.getString("created_by_name"), rs.getLong("version"));
    }

    /** New requests reach the platform team's inbox when one is configured; the queue shows them regardless. */
    private void tellPlatform(Request r) {
        if (platformInbox == null || platformInbox.isBlank()) {
            return;
        }
        var result = email.send(platformInbox, "Integration request " + r.reference() + ": " + r.connectorName(),
                "A client asked for the " + r.connectorName() + " connector.\n\nSystem: " + r.systemDetails() + "\nData: "
                        + String.join(", ", r.dataNeeded()) + " (" + r.frequency() + ")\nContact: " + r.contactName() + " <" + r.contactEmail()
                        + ">\n\nOpen Administration › Integration requests to review it.");
        log.info("Integration request {} email to the platform inbox: {}", r.reference(), result);
    }

    /** The client's contact hears about each status change (email, when configured). */
    private void tellRequester(PlatformRequest r) {
        var result = email.send(r.contactEmail(), "Your " + r.connectorName() + " integration request (" + r.reference() + ")",
                "Hello " + r.contactName() + ",\n\nYour request is now: " + label(r.status()) + "."
                        + (r.statusNote() == null ? "" : "\n\n" + r.statusNote()) + "\n\nAtheris ESG");
        log.info("Integration request {} status email: {}", r.reference(), result);
    }

    static String label(String status) {
        return switch (status) {
            case "in_review" -> "in review";
            case "scheduled" -> "scheduled";
            case "active" -> "active — the connector is live";
            case "declined" -> "declined";
            default -> status;
        };
    }

    private static List<String> list(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
