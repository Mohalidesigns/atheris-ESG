package com.esg.supplychain;

import com.esg.carbon.CarbonDtos.EmissionRequest;
import com.esg.carbon.EmissionService;
import com.esg.datahub.TabularReader;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.security.OrgScopeService;
import com.esg.supplychain.SupplyDtos.ImportResult;
import com.esg.supplychain.SupplyDtos.Spend;
import com.esg.supplychain.SupplyDtos.SpendRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Supplier spend → Scope 3 Category 1 (WP-31; TRD §8.6 Scope3Category1Estimator). Each spend line is calculated by the
 * Carbon engine with the spend-based method and an EEIO factor for its purchase category, and kept in step: editing the
 * line recalculates its emission record, removing it removes the record. Spend can be imported from CSV/Excel.
 */
@Service
public class SpendService {

    /** Columns of a spend import, in this order (header names are matched case-insensitively). */
    static final List<String> COLUMNS = List.of("supplier", "organisation", "period_start", "period_end", "amount", "currency", "category", "description");

    private static final String SELECT = "select p.*, s.name as supplier_name, o.name as org_name, a.label as activity_label, e.co2e_tonnes "
            + "from supply_chain.supplier_spend p join supply_chain.suppliers s on s.id = p.supplier_id join auth.organisations o on o.id = p.org_id "
            + "left join carbon.emission_activities a on a.category = p.source_activity and (a.tenant_id is null or a.tenant_id = p.tenant_id) "
            + "left join carbon.emission_records e on e.id = p.emission_record_id and e.deleted_at is null ";

    private final JdbcTemplate jdbc;
    private final SupplySupport support;
    private final EmissionService emissions;
    private final OrgScopeService orgScope;
    private final TransactionTemplate perRow;

    public SpendService(JdbcTemplate jdbc, SupplySupport support, EmissionService emissions, OrgScopeService orgScope,
                        PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.support = support;
        this.emissions = emissions;
        this.orgScope = orgScope;
        this.perRow = new TransactionTemplate(txManager);
        this.perRow.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<Spend> list(UUID supplierId, Integer year) {
        StringBuilder where = new StringBuilder("where p.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (supplierId != null) {
            where.append(" and p.supplier_id = ?");
            args.add(supplierId);
        }
        if (year != null) {
            where.append(" and extract(year from p.reporting_period_end) = ?");
            args.add(year);
        }
        support.scope(where, args, "p.org_id");
        return jdbc.query(SELECT + where + " order by p.reporting_period_end desc, s.name", (rs, i) -> spend(rs), args.toArray());
    }

    public Spend get(UUID id) {
        Spend s = jdbc.query(SELECT + "where p.id = ? and p.deleted_at is null", (rs, i) -> spend(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Spend line"));
        support.visible(s.orgId(), "Spend line");
        return s;
    }

    /** Spend categories that map to Scope 3 Category 1 (purchased goods and services): code → label. */
    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("select category as code, label from carbon.emission_activities where scope = 'scope_3' and scope3_category = 1 "
                + "and factor_method = 'spend' and (tenant_id is null or tenant_id = auth.current_tenant_id()) order by label");
    }

    @Transactional
    public Spend create(SpendRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into supply_chain.supplier_spend (id, supplier_id, org_id, reporting_period_start, reporting_period_end, amount, currency, "
                        + "source_activity, description) values (?, ?, ?, ?, ?, ?, ?, ?, ?)", id, r.supplierId(), r.orgId(), Date.valueOf(r.reportingPeriodStart()),
                Date.valueOf(r.reportingPeriodEnd()), r.amount(), r.currency(), r.sourceActivity(), SupplierService.blank(r.description()));
        link(id, r);
        return get(id);
    }

    @Transactional
    public Spend update(UUID id, SpendRequest r) {
        get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        check(r);
        int n = jdbc.update("update supply_chain.supplier_spend set supplier_id = ?, org_id = ?, reporting_period_start = ?, reporting_period_end = ?, "
                        + "amount = ?, currency = ?, source_activity = ?, description = ?, version = version + 1 where id = ? and version = ? and deleted_at is null",
                r.supplierId(), r.orgId(), Date.valueOf(r.reportingPeriodStart()), Date.valueOf(r.reportingPeriodEnd()), r.amount(), r.currency(),
                r.sourceActivity(), SupplierService.blank(r.description()), id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This spend line was changed by someone else. Reload and try again.");
        }
        link(id, r);
        return get(id);
    }

    @Transactional
    public void delete(UUID id, long version) {
        get(id);
        if (jdbc.update("update supply_chain.supplier_spend set deleted_at = now(), version = version + 1 where id = ? and version = ? and deleted_at is null",
                id, version) == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This spend line was changed by someone else. Reload and try again.");
        }
        emissions.removeForSupplierSpend(id);
    }

    /**
     * Imports spend lines: one row per line with supplier (name), organisation (name), period_start, period_end, amount,
     * currency, category (a Scope 3 Category 1 spend category code or label) and description. Each row is saved on its
     * own; rows with problems are reported, not saved.
     */
    public ImportResult importFile(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String format = name.endsWith(".xlsx") ? "xlsx" : name.endsWith(".csv") ? "csv" : null;
        if (format == null) {
            throw FieldValidationException.of("file", "upload a .csv or .xlsx file");
        }
        List<List<String>> rows;
        try {
            rows = TabularReader.read(file.getInputStream(), format);
        } catch (IOException e) {
            throw FieldValidationException.of("file", e.getMessage());
        }
        if (rows.isEmpty()) {
            throw FieldValidationException.of("file", "the file is empty");
        }
        if (rows.size() - 1 > TabularReader.MAX_ROWS) {
            throw FieldValidationException.of("file", "at most " + TabularReader.MAX_ROWS + " rows per file");
        }
        Map<String, Integer> header = new LinkedHashMap<>();
        for (int i = 0; i < rows.get(0).size(); i++) {
            header.put(rows.get(0).get(i).trim().toLowerCase(Locale.ROOT).replace(' ', '_'), i);
        }
        List<String> missing = COLUMNS.stream().filter(c -> !"description".equals(c) && !header.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw FieldValidationException.of("file", "missing column(s): " + String.join(", ", missing) + " (expected " + String.join(", ", COLUMNS) + ")");
        }
        Map<String, String> categories = new LinkedHashMap<>();
        categories().forEach(c -> {
            categories.put(((String) c.get("code")).toLowerCase(Locale.ROOT), (String) c.get("code"));
            categories.put(((String) c.get("label")).toLowerCase(Locale.ROOT), (String) c.get("code"));
        });
        List<Map<String, Object>> errors = new ArrayList<>();
        int created = 0;
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            int line = r + 1;
            try {
                SpendRequest req = parse(row, header, categories);
                perRow.executeWithoutResult(s -> create(req));
                created++;
            } catch (FieldValidationException e) {
                errors.add(Map.of("row", line, "message", String.join("; ", e.getErrors().entrySet().stream().map(x -> x.getKey() + " " + x.getValue()).toList())));
            } catch (ApiException e) {
                errors.add(Map.of("row", line, "message", e.getMessage()));
            } catch (RuntimeException e) {
                errors.add(Map.of("row", line, "message", firstLine(e.getMessage())));
            }
        }
        return new ImportResult(rows.size() - 1, created, errors);
    }

    private SpendRequest parse(List<String> row, Map<String, Integer> header, Map<String, String> categories) {
        java.util.function.Function<String, String> cell = c -> header.containsKey(c) && header.get(c) < row.size() ? row.get(header.get(c)).trim() : "";
        Map<String, String> errors = new LinkedHashMap<>();
        List<UUID> supplier = jdbc.queryForList("select id from supply_chain.suppliers where lower(name) = lower(?) and deleted_at is null", UUID.class,
                cell.apply("supplier"));
        if (supplier.isEmpty()) {
            errors.put("supplier", "'" + cell.apply("supplier") + "' isn't in the supplier register");
        }
        List<UUID> org = jdbc.queryForList("select id from auth.organisations where lower(name) = lower(?) and deleted_at is null", UUID.class,
                cell.apply("organisation"));
        if (org.isEmpty()) {
            errors.put("organisation", "'" + cell.apply("organisation") + "' isn't an organisation");
        }
        LocalDate start = date(cell.apply("period_start"), "period_start", errors);
        LocalDate end = date(cell.apply("period_end"), "period_end", errors);
        BigDecimal amount = null;
        try {
            amount = new BigDecimal(cell.apply("amount").replace(",", ""));
            if (amount.signum() <= 0) {
                errors.put("amount", "must be more than 0");
            }
        } catch (NumberFormatException e) {
            errors.put("amount", "must be a number");
        }
        String currency = cell.apply("currency").toUpperCase(Locale.ROOT);
        if (!currency.matches(SupplyDtos.CURRENCY_REGEX)) {
            errors.put("currency", "must be one of NGN, USD, EUR, GBP, KES, ZAR, GHS, XOF, EGP");
        }
        String category = categories.get(cell.apply("category").toLowerCase(Locale.ROOT));
        if (category == null) {
            errors.put("category", "'" + cell.apply("category") + "' isn't a purchased goods and services spend category");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        String description = cell.apply("description");
        return new SpendRequest(supplier.get(0), org.get(0), start, end, amount, currency, category, description.isEmpty() ? null : description, null);
    }

    private static LocalDate date(String v, String field, Map<String, String> errors) {
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            errors.put(field, "must be a date (YYYY-MM-DD)");
            return null;
        }
    }

    private static String firstLine(String m) {
        return m == null ? "could not be saved" : m.split("\n")[0];
    }

    private void check(SpendRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from supply_chain.suppliers where id = ? and deleted_at is null)", Boolean.class,
                r.supplierId()))) {
            errors.put("supplierId", "must be a supplier");
        }
        if (r.reportingPeriodEnd().isBefore(r.reportingPeriodStart())) {
            errors.put("reportingPeriodEnd", "must be on or after the start date");
        }
        if (r.reportingPeriodEnd().isAfter(support.today())) {
            errors.put("reportingPeriodEnd", "spend is recorded for periods that have ended");
        }
        if (categories().stream().noneMatch(c -> r.sourceActivity().equals(c.get("code")))) {
            errors.put("sourceActivity", "must be a purchased goods and services spend category");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        support.requireOrg(r.orgId());
        orgScope.requireAccess(r.orgId());
    }

    /** Creates or recalculates the line's Scope 3 Category 1 record (the Carbon engine's spend-based method). */
    private void link(UUID spendId, SpendRequest r) {
        String supplier = jdbc.queryForObject("select name from supply_chain.suppliers where id = ?", String.class, r.supplierId());
        String notes = ("Supplier spend: " + supplier + (r.description() == null ? "" : " — " + r.description().trim()));
        UUID record = emissions.upsertForSupplierSpend(spendId, new EmissionRequest(r.orgId(), "scope_3", 1, "spend_based", r.sourceActivity(), null,
                null, r.amount(), r.currency(), null, r.reportingPeriodStart(), r.reportingPeriodEnd(), notes.length() > 500 ? notes.substring(0, 500) : notes,
                null));
        jdbc.update("update supply_chain.supplier_spend set emission_record_id = ? where id = ?", record, spendId);
    }

    private static Spend spend(ResultSet rs) throws SQLException {
        return new Spend(rs.getObject("id", UUID.class), rs.getObject("supplier_id", UUID.class), rs.getString("supplier_name"),
                rs.getObject("org_id", UUID.class), rs.getString("org_name"), rs.getObject("reporting_period_start", LocalDate.class),
                rs.getObject("reporting_period_end", LocalDate.class), rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("source_activity"),
                rs.getString("activity_label"), rs.getString("description"), rs.getObject("emission_record_id", UUID.class), rs.getBigDecimal("co2e_tonnes"),
                rs.getLong("version"));
    }
}
