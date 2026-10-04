package com.esg.risk;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.risk.RiskDtos.Heatmap;
import com.esg.risk.RiskDtos.HeatmapGroup;
import com.esg.risk.RiskDtos.Risk;
import com.esg.risk.RiskDtos.RiskRequest;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ESG risk register and heatmaps (WP-32; TRD §8.5 RiskHeatmapGenerator). A risk belongs to an organisation and carries
 * likelihood × impact before and after controls; the heatmap counts open risks on the 5 × 5 grid for the whole
 * register (portfolio), per organisation with drill-down through the tree (entity), or per ISIC sector (sector).
 */
@Service
public class RiskRegisterService {

    private static final String SELECT = "select r.*, o.name as org_name, s.name as sector_name, u.first_name || ' ' || u.last_name as owner_name, "
            + "i.topic as topic_name from risk.risk_register r join auth.organisations o on o.id = r.org_id "
            + "left join reference.sectors s on s.code = r.sector_code left join auth.users u on u.id = r.owner_user_id "
            + "left join risk.materiality_items i on i.id = r.topic_id ";
    private static final Set<String> OPEN = Set.of("open", "mitigating", "accepted");

    private final JdbcTemplate jdbc;
    private final RiskSupport support;

    public RiskRegisterService(JdbcTemplate jdbc, RiskSupport support) {
        this.jdbc = jdbc;
        this.support = support;
    }

    public List<Risk> list(String category, String status, UUID orgId) {
        StringBuilder where = new StringBuilder("where r.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (category != null) {
            if (!category.matches(RiskDtos.RISK_CATEGORY_REGEX)) {
                throw FieldValidationException.of("category", "must be a risk category");
            }
            where.append(" and r.category = ?");
            args.add(category);
        }
        if (status != null) {
            if (!status.matches(RiskDtos.RISK_STATUS_REGEX)) {
                throw FieldValidationException.of("status", "must be open, mitigating, accepted or closed");
            }
            where.append(" and r.status = ?");
            args.add(status);
        }
        if (orgId != null) {
            where.append(" and r.org_id in (").append(SUBTREE).append(")");
            args.add(orgId);
        }
        support.scope(where, args, "r.org_id");
        LocalDate today = support.today();
        return jdbc.query(SELECT + where + " order by coalesce(r.residual_likelihood * r.residual_impact, r.likelihood * r.impact) desc, "
                + "r.likelihood * r.impact desc, lower(r.title)", (rs, i) -> risk(rs, today), args.toArray());
    }

    public Risk get(UUID id) {
        Risk r = jdbc.query(SELECT + "where r.id = ? and r.deleted_at is null", (rs, i) -> risk(rs, support.today()), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Risk"));
        support.visible(r.orgId(), "Risk");
        return r;
    }

    @Transactional
    public Risk create(RiskRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into risk.risk_register (id, org_id, title, category, sector_code, description, time_horizon, likelihood, impact, "
                        + "controls, residual_likelihood, residual_impact, owner_user_id, status, review_date, topic_id) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, r.orgId(), r.title().trim(), r.category(),
                MaterialityService.blank(r.sectorCode()), MaterialityService.blank(r.description()), r.timeHorizon(), r.likelihood(), r.impact(),
                MaterialityService.blank(r.controls()), r.residualLikelihood(), r.residualImpact(), r.ownerUserId(), r.status(),
                r.reviewDate() == null ? null : Date.valueOf(r.reviewDate()), r.topicId());
        return get(id);
    }

    @Transactional
    public Risk update(UUID id, RiskRequest r) {
        get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        check(r);
        int n = jdbc.update("update risk.risk_register set org_id = ?, title = ?, category = ?, sector_code = ?, description = ?, time_horizon = ?, "
                        + "likelihood = ?, impact = ?, controls = ?, residual_likelihood = ?, residual_impact = ?, owner_user_id = ?, status = ?, "
                        + "review_date = ?, topic_id = ?, version = version + 1 where id = ? and version = ? and deleted_at is null", r.orgId(),
                r.title().trim(), r.category(), MaterialityService.blank(r.sectorCode()), MaterialityService.blank(r.description()), r.timeHorizon(),
                r.likelihood(), r.impact(), MaterialityService.blank(r.controls()), r.residualLikelihood(), r.residualImpact(), r.ownerUserId(),
                r.status(), r.reviewDate() == null ? null : Date.valueOf(r.reviewDate()), r.topicId(), id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This risk was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id, long version) {
        get(id);
        int n = jdbc.update("update risk.risk_register set deleted_at = now(), version = version + 1 where id = ? and version = ? "
                + "and deleted_at is null", id, version);
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This risk was changed by someone else. Reload and try again.");
        }
    }

    // ---------------------------------------------------------------- heatmap

    /**
     * Open risks (open, mitigating, accepted) on the 5 × 5 grid — grid[likelihood − 1][impact − 1] — for the register in
     * the user's scope, with groups for the level: entity = the children of {@code orgId} (or the top organisations),
     * each with its subtree; sector = ISIC sector; portfolio = no groups.
     */
    public Heatmap heatmap(String level, boolean residual, UUID orgId) {
        if (!Set.of("portfolio", "entity", "sector").contains(level)) {
            throw FieldValidationException.of("level", "must be portfolio, entity or sector");
        }
        List<Risk> risks = list(null, null, orgId).stream().filter(r -> OPEN.contains(r.status())).toList();
        Map<String, Integer> byBand = new LinkedHashMap<>();
        for (String b : List.of("very_high", "high", "medium", "low")) {
            byBand.put(b, (int) risks.stream().filter(r -> b.equals(residual ? residualBand(r) : r.band())).count());
        }
        List<HeatmapGroup> groups = new ArrayList<>();
        if ("entity".equals(level)) {
            List<Map<String, Object>> children = orgId == null
                    ? jdbc.queryForList("select id, name from auth.organisations o where o.deleted_at is null and (o.parent_org_id is null "
                            + "or o.parent_org_id not in (select id from auth.organisations where deleted_at is null)) order by name")
                    : jdbc.queryForList("select id, name from auth.organisations where parent_org_id = ? and deleted_at is null order by name", orgId);
            if (orgId != null) {
                // Risks recorded at the selected organisation itself.
                Map<String, Object> self = jdbc.queryForMap("select id, name from auth.organisations where id = ?", orgId);
                List<Risk> own = risks.stream().filter(r -> r.orgId().equals(orgId)).toList();
                if (!own.isEmpty()) {
                    groups.add(group(orgId.toString(), self.get("name") + " (itself)", orgId, false, own, residual));
                }
            }
            for (Map<String, Object> c : children) {
                UUID child = (UUID) c.get("id");
                if (!support.canSee(child) && !support.anyInScope(subtree(child))) {
                    continue;
                }
                Set<UUID> tree = Set.copyOf(subtree(child));
                List<Risk> inTree = risks.stream().filter(r -> tree.contains(r.orgId())).toList();
                groups.add(group(child.toString(), (String) c.get("name"), child, tree.size() > 1, inTree, residual));
            }
        } else if ("sector".equals(level)) {
            Map<String, List<Risk>> bySector = new LinkedHashMap<>();
            risks.forEach(r -> bySector.computeIfAbsent(r.sectorCode() == null ? "" : r.sectorCode(), k -> new ArrayList<>()).add(r));
            bySector.forEach((code, list) -> groups.add(group(code, code.isEmpty() ? "No sector" : code + " · " + list.get(0).sectorName(), null,
                    false, list, residual)));
            groups.sort((a, b) -> Integer.compare(b.maxScore(), a.maxScore()));
        }
        return new Heatmap(level, residual, risks.size(), grid(risks, residual), byBand, groups);
    }

    private static final String SUBTREE = "with recursive t as (select id from auth.organisations where id = ? "
            + "union select o.id from auth.organisations o join t on o.parent_org_id = t.id) select id from t";

    private List<UUID> subtree(UUID org) {
        return jdbc.queryForList(SUBTREE, UUID.class, org);
    }

    private static HeatmapGroup group(String key, String label, UUID orgId, boolean hasChildren, List<Risk> risks, boolean residual) {
        int max = risks.stream().mapToInt(r -> residual ? residualScore(r) : r.score()).max().orElse(0);
        return new HeatmapGroup(key, label, orgId, hasChildren, risks.size(), grid(risks, residual), max, max == 0 ? null : RiskMath.band(max));
    }

    private static int[][] grid(List<Risk> risks, boolean residual) {
        int[][] g = new int[5][5];
        Function<Risk, int[]> at = r -> residual && r.residualLikelihood() != null
                ? new int[] {r.residualLikelihood(), r.residualImpact()} : new int[] {r.likelihood(), r.impact()};
        for (Risk r : risks) {
            int[] p = at.apply(r);
            g[p[0] - 1][p[1] - 1]++;
        }
        return g;
    }

    private static int residualScore(Risk r) {
        return r.residualScore() == null ? r.score() : r.residualScore();
    }

    private static String residualBand(Risk r) {
        return RiskMath.band(residualScore(r));
    }

    private void check(RiskRequest r) {
        support.requireOrg(r.orgId());
        Map<String, String> errors = new LinkedHashMap<>();
        if ((r.residualLikelihood() == null) != (r.residualImpact() == null)) {
            errors.put(r.residualLikelihood() == null ? "residualLikelihood" : "residualImpact", "give both residual scores, or neither");
        } else if (r.residualLikelihood() != null && r.residualLikelihood() * r.residualImpact() > r.likelihood() * r.impact()) {
            errors.put("residualImpact", "the risk after controls can't score higher than before them");
        }
        if (r.sectorCode() != null && !r.sectorCode().isBlank() && !Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from reference.sectors where code = ?)", Boolean.class, r.sectorCode()))) {
            errors.put("sectorCode", "must be a sector from the catalogue");
        }
        if (r.ownerUserId() != null && !support.holds(r.ownerUserId(), "update")) {
            errors.put("ownerUserId", "must be someone who can update risks");
        }
        if (r.topicId() != null && !Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from risk.materiality_items where id = ? "
                + "and deleted_at is null)", Boolean.class, r.topicId()))) {
            errors.put("topicId", "must be a materiality topic");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private static Risk risk(ResultSet rs, LocalDate today) throws SQLException {
        int l = rs.getInt("likelihood");
        int i = rs.getInt("impact");
        Integer rl = (Integer) rs.getObject("residual_likelihood");
        Integer ri = (Integer) rs.getObject("residual_impact");
        Integer rscore = rl == null ? null : rl * ri;
        LocalDate review = rs.getObject("review_date", LocalDate.class);
        String status = rs.getString("status");
        return new Risk(rs.getObject("id", UUID.class), rs.getObject("org_id", UUID.class), rs.getString("org_name"), rs.getString("title"),
                rs.getString("category"), rs.getString("sector_code"), rs.getString("sector_name"), rs.getString("description"),
                rs.getString("time_horizon"), l, i, l * i, RiskMath.band(l * i), rs.getString("controls"), rl, ri, rscore,
                rscore == null ? null : RiskMath.band(rscore), rs.getObject("owner_user_id", UUID.class), rs.getString("owner_name"), status,
                review, review != null && !"closed".equals(status) && review.isBefore(today), rs.getObject("topic_id", UUID.class),
                rs.getString("topic_name"), rs.getLong("version"));
    }
}
