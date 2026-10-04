package com.esg.governance;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.governance.GovernanceDtos.BoardMember;
import com.esg.governance.GovernanceDtos.BoardMemberRequest;
import com.esg.governance.GovernanceDtos.CheckerReport;
import com.esg.governance.GovernanceDtos.Composition;
import com.esg.governance.GovernanceDtos.RuleResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Board register, composition and the governance code checker (WP-34; TRD §8.8 BoardCompositionTracker). */
@Service
public class BoardService {

    static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public BoardService(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(LAGOS));
    }

    /** Members sitting on the board on {@code asOf} (or everyone, with leavers, when {@code includeFormer}). */
    public List<BoardMember> list(LocalDate asOf, boolean includeFormer) {
        LocalDate day = asOf == null ? today() : asOf;
        String where = includeFormer ? "where deleted_at is null"
                : "where deleted_at is null and appointed_on <= ? and (left_on is null or left_on >= ?)";
        Object[] args = includeFormer ? new Object[0] : new Object[] {Date.valueOf(day), Date.valueOf(day)};
        return jdbc.query("select * from governance.board_members " + where + " order by array_position(array['chair','vice_chair',"
                + "'md_ceo','executive_director','independent_non_executive_director','non_executive_director','company_secretary'], board_role), "
                + "lower(name)", (rs, i) -> member(rs, day), args);
    }

    public BoardMember get(UUID id) {
        return jdbc.query("select * from governance.board_members where id = ? and deleted_at is null", (rs, i) -> member(rs, today()), id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Board member"));
    }

    @Transactional
    public BoardMember create(BoardMemberRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into governance.board_members (id, name, board_role, is_independent, is_executive, gender, "
                    + "age_band, appointed_on, left_on, committees, skills, notes, is_active) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)");
            bind(ps, con, r, id);
            return ps;
        });
        return get(id);
    }

    @Transactional
    public BoardMember update(UUID id, BoardMemberRequest r) {
        get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        check(r);
        int n = jdbc.update(con -> {
            var ps = con.prepareStatement("update governance.board_members set name = ?, board_role = ?, is_independent = ?, is_executive = ?, "
                    + "gender = ?, age_band = ?, appointed_on = ?, left_on = ?, committees = ?, skills = ?, notes = ?, version = version + 1 "
                    + "where id = ? and version = ? and deleted_at is null");
            ps.setString(1, r.name().trim());
            ps.setString(2, r.boardRole());
            ps.setBoolean(3, r.independent());
            ps.setBoolean(4, r.executive());
            ps.setString(5, r.gender());
            ps.setString(6, r.ageBand());
            ps.setDate(7, Date.valueOf(r.appointedOn()));
            ps.setDate(8, r.leftOn() == null ? null : Date.valueOf(r.leftOn()));
            ps.setArray(9, con.createArrayOf("text", list(r.committees()).toArray()));
            ps.setArray(10, con.createArrayOf("text", skills(r.skills()).toArray()));
            ps.setString(11, blank(r.notes()));
            ps.setObject(12, id);
            ps.setLong(13, r.version());
            return ps;
        });
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This board member was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    /** Removes a record entered by mistake (a director who leaves gets a leaving date instead). Soft delete, audited. */
    @Transactional
    public void delete(UUID id, long version) {
        int n = jdbc.update("update governance.board_members set deleted_at = now(), version = version + 1 where id = ? and version = ? "
                + "and deleted_at is null", id, version);
        if (n == 0) {
            get(id);
            throw ApiException.conflict("ESG-409-VERSION", "This board member was changed by someone else. Reload and try again.");
        }
    }

    // ---------------------------------------------------------------- composition and checks

    public Composition composition(LocalDate asOf) {
        LocalDate day = asOf == null ? today() : asOf;
        List<BoardMember> all = list(day, false);
        List<BoardMember> d = all.stream().filter(m -> !"company_secretary".equals(m.boardRole())).toList();
        int exec = (int) d.stream().filter(BoardMember::executive).count();
        int ind = (int) d.stream().filter(GovernanceChecker::independent).count();
        int female = (int) d.stream().filter(m -> "female".equals(m.gender())).count();
        List<BigDecimal> tenures = d.stream().map(BoardMember::tenureYears).filter(t -> t != null).toList();
        Map<String, Integer> bands = new LinkedHashMap<>();
        for (String b : List.of("under_40", "40_49", "50_59", "60_plus")) {
            bands.put(b, (int) d.stream().filter(m -> b.equals(m.ageBand())).count());
        }
        Map<String, Integer> committees = new TreeMap<>();
        Map<String, Integer> skills = new TreeMap<>();
        d.forEach(m -> {
            m.committees().forEach(c -> committees.merge(c, 1, Integer::sum));
            m.skills().forEach(s -> skills.merge(s, 1, Integer::sum));
        });
        return new Composition(day, d.size(), exec, d.size() - exec, ind, pct(ind, d.size()), female, pct(female, d.size()),
                tenures.isEmpty() ? null : tenures.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(tenures.size()), 1, RoundingMode.HALF_UP),
                bands, committees, skills, all.stream().anyMatch(m -> "company_secretary".equals(m.boardRole())));
    }

    public CheckerReport check(LocalDate asOf, String ruleSet) {
        LocalDate day = asOf == null ? today() : asOf;
        List<BoardMember> active = list(day, false);
        List<GovernanceChecker.Rule> rules = jdbc.query("select * from reference.governance_rules where (cast(? as varchar) is null or rule_set = ?) "
                + "order by sort_order, code", (rs, i) -> {
                    try {
                        return new GovernanceChecker.Rule(rs.getString("code"), rs.getString("rule_set"), rs.getString("provision"),
                                rs.getString("title"), rs.getString("rule_type"), json.readTree(rs.getString("params")), rs.getString("applies_to"),
                                rs.getString("source"), rs.getString("verification_status"));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }
                }, ruleSet, ruleSet);
        List<RuleResult> results = rules.stream().map(r -> GovernanceChecker.check(r, active)).toList();
        int pass = (int) results.stream().filter(r -> "pass".equals(r.result())).count();
        int fail = (int) results.stream().filter(r -> "fail".equals(r.result())).count();
        return new CheckerReport(day, pass, fail, results.size() - pass - fail, results);
    }

    // ---------------------------------------------------------------- internals

    private void check(BoardMemberRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (r.leftOn() != null && r.leftOn().isBefore(r.appointedOn())) {
            errors.put("leftOn", "must be on or after the appointment date");
        }
        if (r.appointedOn().isAfter(today().plusYears(1))) {
            errors.put("appointedOn", "can't be more than a year ahead");
        }
        if (Boolean.TRUE.equals(r.independent()) && Boolean.TRUE.equals(r.executive())) {
            errors.put("independent", "an executive director can't be independent");
        }
        if ("independent_non_executive_director".equals(r.boardRole()) && (!Boolean.TRUE.equals(r.independent()) || Boolean.TRUE.equals(r.executive()))) {
            errors.put("boardRole", "an independent non-executive director is independent and non-executive");
        }
        if ("md_ceo".equals(r.boardRole()) || "executive_director".equals(r.boardRole())) {
            if (!Boolean.TRUE.equals(r.executive())) {
                errors.put("executive", "this role is executive");
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private void bind(java.sql.PreparedStatement ps, java.sql.Connection con, BoardMemberRequest r, UUID id) throws SQLException {
        ps.setObject(1, id);
        ps.setString(2, r.name().trim());
        ps.setString(3, r.boardRole());
        ps.setBoolean(4, r.independent());
        ps.setBoolean(5, r.executive());
        ps.setString(6, r.gender());
        ps.setString(7, r.ageBand());
        ps.setDate(8, Date.valueOf(r.appointedOn()));
        ps.setDate(9, r.leftOn() == null ? null : Date.valueOf(r.leftOn()));
        ps.setArray(10, con.createArrayOf("text", list(r.committees()).toArray()));
        ps.setArray(11, con.createArrayOf("text", skills(r.skills()).toArray()));
        ps.setString(12, blank(r.notes()));
    }

    private static List<String> list(List<String> v) {
        return v == null ? List.of() : v.stream().distinct().toList();
    }

    private static List<String> skills(List<String> v) {
        return v == null ? List.of() : v.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static BigDecimal pct(int n, int of) {
        return of == 0 ? null : BigDecimal.valueOf(100L * n).divide(BigDecimal.valueOf(of), 1, RoundingMode.HALF_UP);
    }

    private static BoardMember member(ResultSet rs, LocalDate asOf) throws SQLException {
        LocalDate appointed = rs.getObject("appointed_on", LocalDate.class);
        LocalDate left = rs.getObject("left_on", LocalDate.class);
        LocalDate end = left != null && left.isBefore(asOf) ? left : asOf;
        BigDecimal tenure = appointed == null || appointed.isAfter(end) ? null
                : BigDecimal.valueOf(ChronoUnit.DAYS.between(appointed, end)).divide(BigDecimal.valueOf(365.25), 1, RoundingMode.HALF_UP);
        boolean active = appointed != null && !appointed.isAfter(asOf) && (left == null || !left.isBefore(asOf));
        return new BoardMember(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("board_role"),
                rs.getBoolean("is_independent"), rs.getBoolean("is_executive"), rs.getString("gender"), rs.getString("age_band"), appointed,
                left, tenure, active, array(rs.getArray("committees")), array(rs.getArray("skills")), rs.getString("notes"), rs.getLong("version"));
    }

    private static List<String> array(Array a) throws SQLException {
        return a == null ? List.of() : Arrays.asList((String[]) a.getArray());
    }
}
