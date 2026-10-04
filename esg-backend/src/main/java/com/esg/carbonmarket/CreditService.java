package com.esg.carbonmarket;

import com.esg.carbonmarket.CarbonMarketDtos.ActionRequest;
import com.esg.carbonmarket.CarbonMarketDtos.Lot;
import com.esg.carbonmarket.CarbonMarketDtos.LotRequest;
import com.esg.carbonmarket.CarbonMarketDtos.Project;
import com.esg.carbonmarket.CarbonMarketDtos.ProjectRequest;
import com.esg.carbonmarket.CarbonMarketDtos.RetireRequest;
import com.esg.carbonmarket.CarbonMarketDtos.Retirement;
import com.esg.carbonmarket.CarbonMarketMath.Range;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.security.CurrentUser;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Offset projects, credit lots and retirements (WP-41, D36 (2)–(5); TRD §8.11 CreditPortfolioManager, RetirementEngine).
 * Serials can't be held twice or retired twice across all tenants (database trigger, ESG-409-SERIAL). A retirement is
 * requested (next free serials reserved), then approved with the registry reference by someone else, or cancelled.
 */
@Service
public class CreditService {

    static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");
    // no-mock-gate: config — nature-based project types (D36 (2))
    static final Set<String> NATURE_BASED = Set.of("reforestation_arr", "mangrove", "redd_plus", "soil_carbon", "blue_carbon", "agroforestry");
    static final Map<String, Set<String>> ACTIONS = Map.of("requested", Set.of("approve", "cancel"), "retired", Set.of(), "cancelled", Set.of());

    private static final String LOT_SELECT = "select l.*, p.name as project_name, p.standard, u.first_name || ' ' || u.last_name as verified_by_name, "
            + "coalesce((select sum(quantity) from carbon_market.retirements r where r.lot_id = l.id and r.status = 'retired'), 0) as retired, "
            + "coalesce((select sum(quantity) from carbon_market.retirements r where r.lot_id = l.id and r.status = 'requested'), 0) as reserved "
            + "from carbon_market.credit_lots l join carbon_market.offset_projects p on p.id = l.project_id left join auth.users u on u.id = l.verified_by ";
    private static final String RET_SELECT = "select r.*, p.name as project_name, p.standard, l.vintage, q.first_name || ' ' || q.last_name as requested_by_name, "
            + "a.first_name || ' ' || a.last_name as approved_by_name from carbon_market.retirements r join carbon_market.credit_lots l on l.id = r.lot_id "
            + "join carbon_market.offset_projects p on p.id = l.project_id left join auth.users q on q.id = r.requested_by left join auth.users a on a.id = r.approved_by ";

    private final JdbcTemplate jdbc;
    private final CarbonRegistry registry;
    private final Clock clock;

    public CreditService(JdbcTemplate jdbc, CarbonRegistry registry, Clock clock) {
        this.jdbc = jdbc;
        this.registry = registry;
        this.clock = clock;
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(LAGOS));
    }

    // ---------------------------------------------------------------- projects

    public List<Project> projects() {
        return jdbc.query(projectSelect() + "where p.deleted_at is null order by p.status, lower(p.name)", (rs, i) -> project(rs));
    }

    public Project project(UUID id) {
        return jdbc.query(projectSelect() + "where p.id = ? and p.deleted_at is null", (rs, i) -> project(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Project"));
    }

    @Transactional
    public Project createProject(ProjectRequest r) {
        checkProject(r, null);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into carbon_market.offset_projects (id, name, standard, registry, registry_project_id, project_type, country_code, methodology, "
                + "registry_url, description, status) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, r.name().trim(), r.standard(), r.registry().trim(),
                r.registryProjectId().trim(), r.projectType(), r.countryCode(), blank(r.methodology()), blank(r.registryUrl()), blank(r.description()), r.status());
        return project(id);
    }

    @Transactional
    public Project updateProject(UUID id, ProjectRequest r) {
        Project p = project(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        checkProject(r, id);
        if (!p.standard().equals(r.standard()) && p.held() > 0) {
            throw FieldValidationException.of("standard", "can't change once credits are recorded against the project");
        }
        int n = jdbc.update("update carbon_market.offset_projects set name = ?, standard = ?, registry = ?, registry_project_id = ?, project_type = ?, "
                + "country_code = ?, methodology = ?, registry_url = ?, description = ?, status = ?, version = version + 1 where id = ? and version = ? "
                + "and deleted_at is null", r.name().trim(), r.standard(), r.registry().trim(), r.registryProjectId().trim(), r.projectType(), r.countryCode(),
                blank(r.methodology()), blank(r.registryUrl()), blank(r.description()), r.status(), id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This project was changed by someone else. Reload and try again.");
        }
        return project(id);
    }

    @Transactional
    public void deleteProject(UUID id) {
        if (project(id).held() > 0) {
            throw ApiException.conflict("ESG-409-IN-USE", "Credits are recorded against this project; archive it instead.");
        }
        jdbc.update("update carbon_market.offset_projects set deleted_at = now(), version = version + 1 where id = ?", id);
    }

    private void checkProject(ProjectRequest r, UUID id) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from carbon_market.offset_projects where standard = ? and "
                + "lower(registry_project_id) = lower(?) and deleted_at is null and id is distinct from ?)", Boolean.class, r.standard(),
                r.registryProjectId().trim(), id))) {
            throw FieldValidationException.of("registryProjectId", "this project is already in your registry list");
        }
    }

    private static String projectSelect() {
        return "select p.*, coalesce((select sum(quantity) from carbon_market.credit_lots l where l.project_id = p.id and l.deleted_at is null), 0) as held, "
                + "coalesce((select sum(r.quantity) from carbon_market.retirements r join carbon_market.credit_lots l on l.id = r.lot_id "
                + "where l.project_id = p.id and r.status = 'retired'), 0) as retired from carbon_market.offset_projects p ";
    }

    private Project project(ResultSet rs) throws SQLException {
        String type = rs.getString("project_type");
        return new Project(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("standard"), rs.getString("registry"),
                rs.getString("registry_project_id"), type, NATURE_BASED.contains(type), rs.getString("country_code"), rs.getString("methodology"),
                rs.getString("registry_url"), rs.getString("description"), rs.getString("status"), rs.getLong("held"), rs.getLong("retired"),
                rs.getLong("version"));
    }

    // ---------------------------------------------------------------- lots

    public List<Lot> lots(UUID projectId) {
        return jdbc.query(LOT_SELECT + "where l.deleted_at is null" + (projectId == null ? "" : " and l.project_id = ?") + " order by l.acquired_on desc, p.name",
                (rs, i) -> lot(rs), projectId == null ? new Object[0] : new Object[] {projectId});
    }

    public Lot lot(UUID id) {
        return jdbc.query(LOT_SELECT + "where l.id = ? and l.deleted_at is null", (rs, i) -> lot(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Credit lot"));
    }

    @Transactional
    public Lot createLot(LotRequest r) {
        String key = checkLot(r);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into carbon_market.credit_lots (id, project_id, vintage, serial_prefix, serial_key, serial_from, serial_to, acquired_on, "
                + "price_per_tonne, currency, seller, transaction_ref, notes) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, r.projectId(), r.vintage(),
                r.serialPrefix().trim(), key, r.serialFrom(), r.serialTo(), Date.valueOf(r.acquiredOn()), r.pricePerTonne(), blank(r.currency()),
                blank(r.seller()), blank(r.transactionRef()), blank(r.notes()));
        return lot(id);
    }

    @Transactional
    public Lot updateLot(UUID id, LotRequest r) {
        Lot current = lot(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        String key = checkLot(r);
        boolean serialsChanged = !current.projectId().equals(r.projectId()) || !current.serialPrefix().equals(r.serialPrefix().trim())
                || current.serialFrom() != r.serialFrom() || current.serialTo() != r.serialTo();
        if (serialsChanged && current.retired() + current.reserved() > 0) {
            throw FieldValidationException.of("serialFrom", "the serials can't change once some are retired or reserved");
        }
        int n = jdbc.update("update carbon_market.credit_lots set project_id = ?, vintage = ?, serial_prefix = ?, serial_key = ?, serial_from = ?, serial_to = ?, "
                + "acquired_on = ?, price_per_tonne = ?, currency = ?, seller = ?, transaction_ref = ?, notes = ?, "
                + (serialsChanged ? "verification_status = 'unverified', verified_by = null, verified_at = null, verification_file_id = null, " : "")
                + "version = version + 1 where id = ? and version = ? and deleted_at is null", r.projectId(), r.vintage(), r.serialPrefix().trim(), key,
                r.serialFrom(), r.serialTo(), Date.valueOf(r.acquiredOn()), r.pricePerTonne(), blank(r.currency()), blank(r.seller()),
                blank(r.transactionRef()), blank(r.notes()), id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This lot was changed by someone else. Reload and try again.");
        }
        return lot(id);
    }

    @Transactional
    public void deleteLot(UUID id) {
        Lot l = lot(id);
        if (l.retired() + l.reserved() > 0) {
            throw ApiException.conflict("ESG-409-IN-USE", "Some of these credits are retired or reserved; the lot stays on record.");
        }
        jdbc.update("update carbon_market.credit_lots set deleted_at = now(), version = version + 1 where id = ?", id);
    }

    /** An approver confirms the holding against the registry statement they attach (carbon_market:approve). */
    @Transactional
    public Lot verifyLot(UUID id, ActionRequest r) {
        lot(id);
        if (r.fileId() == null) {
            throw FieldValidationException.of("fileId", "attach the registry statement that shows the holding");
        }
        int n = jdbc.update("update carbon_market.credit_lots set verification_status = 'verified', verified_by = ?, verified_at = now(), "
                + "verification_file_id = ?, version = version + 1 where id = ? and version = ?", CurrentUser.access().userId(), r.fileId(), id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This lot was changed by someone else. Reload and try again.");
        }
        return lot(id);
    }

    /** What the registry adapter says about the lot's serials (Sandbox: not connected). */
    public Map<String, Object> registryCheck(UUID id) {
        Lot l = lot(id);
        String projectRef = jdbc.queryForObject("select registry_project_id from carbon_market.offset_projects where id = ?", String.class, l.projectId());
        return registry.verify(l.standard(), projectRef, l.standard() + ":" + l.serialPrefix(), l.serialFrom(), l.serialTo());
    }

    private String checkLot(LotRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        List<String> standard = jdbc.queryForList("select standard from carbon_market.offset_projects where id = ? and deleted_at is null", String.class,
                r.projectId());
        if (standard.isEmpty()) {
            errors.put("projectId", "must be one of your projects");
        }
        if (r.serialTo() < r.serialFrom()) {
            errors.put("serialTo", "must be at or after the first serial");
        }
        if ((r.pricePerTonne() == null) != (r.currency() == null || r.currency().isBlank())) {
            errors.put(r.pricePerTonne() == null ? "pricePerTonne" : "currency", "give the price and its currency, or neither");
        }
        if (r.acquiredOn().isAfter(today())) {
            errors.put("acquiredOn", "can't be in the future");
        }
        if (r.vintage() > r.acquiredOn().getYear() + 1) {
            errors.put("vintage", "can't be later than the year after acquisition");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return standard.get(0) + ":" + r.serialPrefix().trim().toUpperCase(java.util.Locale.ROOT);
    }

    private Lot lot(ResultSet rs) throws SQLException {
        long quantity = rs.getLong("quantity");
        long retired = rs.getLong("retired");
        long reserved = rs.getLong("reserved");
        List<String> actions = new ArrayList<>();
        if (CurrentUser.hasPermission("carbon_market:approve") && "unverified".equals(rs.getString("verification_status"))) {
            actions.add("verify");
        }
        if (CurrentUser.hasPermission("carbon_market:create") && quantity - retired - reserved > 0) {
            actions.add("retire");
        }
        return new Lot(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class), rs.getString("project_name"), rs.getString("standard"),
                rs.getInt("vintage"), rs.getString("serial_prefix"), rs.getLong("serial_from"), rs.getLong("serial_to"), quantity, retired, reserved,
                quantity - retired - reserved, rs.getObject("acquired_on", LocalDate.class), rs.getBigDecimal("price_per_tonne"), rs.getString("currency"),
                rs.getString("seller"), rs.getString("transaction_ref"), rs.getString("verification_status"), rs.getString("verified_by_name"),
                instant(rs.getTimestamp("verified_at")), rs.getObject("verification_file_id", UUID.class), rs.getString("notes"), actions, rs.getLong("version"));
    }

    // ---------------------------------------------------------------- retirements

    public List<Retirement> retirements(Integer year, String status) {
        StringBuilder where = new StringBuilder("where true");
        List<Object> args = new ArrayList<>();
        if (year != null) {
            where.append(" and r.claimed_year = ?");
            args.add(year);
        }
        if (status != null) {
            if (!ACTIONS.containsKey(status)) {
                throw FieldValidationException.of("status", "must be requested, retired or cancelled");
            }
            where.append(" and r.status = ?");
            args.add(status);
        }
        return jdbc.query(RET_SELECT + where + " order by r.status <> 'requested', r.created_at desc", (rs, i) -> retirement(rs), args.toArray());
    }

    public Retirement retirement(UUID id) {
        return jdbc.query(RET_SELECT + "where r.id = ?", (rs, i) -> retirement(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Retirement"));
    }

    /** TRD POST /carbon-market/retire: reserves the next free serials of the lot for the year and purpose. */
    @Transactional
    public Retirement request(RetireRequest r) {
        Lot l = lot(r.lotId());
        if (r.claimedYear() > today().getYear()) {
            throw FieldValidationException.of("claimedYear", "can't offset a year that hasn't started");
        }
        if (r.claimedYear() < l.vintage() - 5) {
            throw FieldValidationException.of("claimedYear", "is more than five years before the credits' vintage (" + l.vintage() + ")");
        }
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtext(?))", Object.class, l.standard() + ":" + l.serialPrefix().toUpperCase(java.util.Locale.ROOT));
        List<Range> used = jdbc.query("select serial_from, serial_to from carbon_market.retirements where lot_id = ? and status <> 'cancelled'",
                (rs, i) -> new Range(rs.getLong(1), rs.getLong(2)), r.lotId());
        Range block = CarbonMarketMath.nextFree(l.serialFrom(), l.serialTo(), used, r.quantity());
        if (block == null) {
            long free = CarbonMarketMath.free(l.serialFrom(), l.serialTo(), used);
            throw FieldValidationException.of("quantity", free < r.quantity() ? "only " + free + " credits of this lot are still available"
                    : "no " + r.quantity() + " consecutive serials are free; retire in smaller parts");
        }
        UUID id = UUID.randomUUID();
        String key = jdbc.queryForObject("select serial_key from carbon_market.credit_lots where id = ?", String.class, r.lotId());
        jdbc.update("insert into carbon_market.retirements (id, lot_id, serial_key, serial_from, serial_to, purpose, claimed_year, beneficiary, note, requested_by) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, r.lotId(), key, block.from(), block.to(), r.purpose(), r.claimedYear(), r.beneficiary().trim(),
                blank(r.note()), CurrentUser.access().userId());
        return retirement(id);
    }

    @Transactional
    public Retirement act(UUID id, ActionRequest r) {
        Retirement ret = retirement(id);
        String action = r.action();
        String perm = "approve".equals(action) ? "carbon_market:approve" : "carbon_market:create";
        if (!Set.of("approve", "cancel").contains(action)) {
            throw FieldValidationException.of("action", "must be approve or cancel");
        }
        if (!CurrentUser.hasPermission(perm)) {
            throw ApiException.forbidden("You need the " + perm + " permission to " + action + " a retirement.");
        }
        if (!ret.actions().contains(action)) {
            String blocked = ret.blockedActions().get(action);
            if (blocked != null) {
                throw ApiException.conflict("ESG-409-SOD", blocked);
            }
            throw ApiException.conflict("ESG-409-STATE", "You can't " + action + " a retirement that is " + ret.status() + ".");
        }
        UUID me = CurrentUser.access().userId();
        int n;
        if ("approve".equals(action)) {
            Map<String, String> errors = new LinkedHashMap<>();
            if (r.registryReference() == null || r.registryReference().isBlank()) {
                errors.put("registryReference", "give the registry's retirement reference");
            }
            if (r.retiredOn() == null) {
                errors.put("retiredOn", "give the date the registry retired the credits");
            } else if (r.retiredOn().isAfter(today())) {
                errors.put("retiredOn", "can't be in the future");
            }
            if (!errors.isEmpty()) {
                throw new FieldValidationException(errors);
            }
            n = jdbc.update("update carbon_market.retirements set status = 'retired', approved_by = ?, retired_on = ?, registry_reference = ?, "
                    + "certificate_file_id = ?, version = version + 1 where id = ? and version = ?", me, Date.valueOf(r.retiredOn()), r.registryReference().trim(),
                    r.fileId(), id, r.version());
        } else {
            if (r.note() == null || r.note().isBlank()) {
                throw FieldValidationException.of("note", "say why the retirement is cancelled");
            }
            n = jdbc.update("update carbon_market.retirements set status = 'cancelled', cancelled_by = ?, cancel_reason = ?, version = version + 1 "
                    + "where id = ? and version = ?", me, r.note().trim(), id, r.version());
        }
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This retirement was changed by someone else. Reload and try again.");
        }
        return retirement(id);
    }

    private Retirement retirement(ResultSet rs) throws SQLException {
        String status = rs.getString("status");
        UUID requestedBy = rs.getObject("requested_by", UUID.class);
        UUID me = CurrentUser.userIdOrNull();
        List<String> actions = new ArrayList<>();
        Map<String, String> blocked = new LinkedHashMap<>();
        for (String a : ACTIONS.get(status)) {
            if (!CurrentUser.hasPermission("approve".equals(a) ? "carbon_market:approve" : "carbon_market:create")) {
                continue;
            }
            if ("approve".equals(a) && me != null && me.equals(requestedBy)) {
                blocked.put(a, "You requested this retirement, so someone else must approve it.");
            } else {
                actions.add(a);
            }
        }
        return new Retirement(rs.getObject("id", UUID.class), rs.getObject("lot_id", UUID.class), rs.getString("project_name"), rs.getString("standard"),
                rs.getInt("vintage"), rs.getString("serial_key"), rs.getLong("serial_from"), rs.getLong("serial_to"), rs.getLong("quantity"),
                rs.getString("purpose"), rs.getInt("claimed_year"), rs.getString("beneficiary"), rs.getString("note"), status, rs.getString("requested_by_name"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("approved_by_name"), rs.getObject("retired_on", LocalDate.class),
                rs.getString("registry_reference"), rs.getObject("certificate_file_id", UUID.class), rs.getString("cancel_reason"), actions, blocked,
                rs.getLong("version"));
    }

    static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
