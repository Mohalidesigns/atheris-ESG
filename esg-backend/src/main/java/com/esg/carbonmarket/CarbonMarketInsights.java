package com.esg.carbonmarket;

import com.esg.carbonmarket.CarbonMarketDtos.Adapter;
import com.esg.carbonmarket.CarbonMarketDtos.Offsetting;
import com.esg.carbonmarket.CarbonMarketDtos.Overview;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** What the carbon market gives other modules (D36 (8)): report variables, the dashboard card, adapter status. */
@Component
public class CarbonMarketInsights {

    // no-mock-gate: config — carbon market report variables (WP-41)
    public static final Set<String> VARIABLES = Set.of("credits_retired", "net_scope12", "offset_share", "offset_to_reduction");

    public record Value(String text, BigDecimal number, String unit, String basis) {}

    private final JdbcTemplate jdbc;
    private final PricingService pricing;
    private final CarbonRegistry registry;
    private final CarbonExchange exchange;

    public CarbonMarketInsights(JdbcTemplate jdbc, PricingService pricing, CarbonRegistry registry, CarbonExchange exchange) {
        this.jdbc = jdbc;
        this.pricing = pricing;
        this.registry = registry;
        this.exchange = exchange;
    }

    /** {@code offsets:<key>} for the report's end year. */
    public Optional<Value> variable(String key, int year) {
        Offsetting o = pricing.offsetting(year);
        BigDecimal credits = o.creditsScope1().add(o.creditsScope2()).add(o.creditsScope3()).add(o.creditsVoluntary());
        String basis = "credits retired against " + year + " (carbon market)";
        return switch (key) {
            case "credits_retired" -> Optional.of(new Value(credits.toPlainString() + " tCO2e", credits, "tCO2e", basis));
            case "net_scope12" -> Optional.of(new Value(o.netScope12().toPlainString() + " tCO2e", o.netScope12(), "tCO2e",
                    "gross Scope 1+2 less credits retired against them; the gross figure stays the reported inventory"));
            case "offset_share" -> Optional.ofNullable(o.offsetShare()).map(v -> new Value(v.toPlainString() + "%", v, "%", "credits ÷ gross Scope 1+2"));
            default -> Optional.ofNullable(o.offsetToReduction()).map(v -> new Value(v.toPlainString(), v, null, "credits ÷ reductions against " + o.baselineBasis()));
        };
    }

    public Adapter registryStatus() {
        return new Adapter(registry.name(), registry.sandbox(), registry.sandbox() ? "sandbox" : "connected",
                registry.sandbox() ? "No carbon registry is connected; holdings are verified from attached registry statements" : registry.name());
    }

    public Adapter exchangeStatus() {
        Map<String, Object> r = exchange.listings("");
        return new Adapter(exchange.name(), exchange.sandbox(), exchange.sandbox() ? "sandbox" : "connected", String.valueOf(r.get("message")));
    }

    public Overview overview(int year) {
        Map<String, Object> held = jdbc.queryForMap("select coalesce(sum(l.quantity), 0) as held, coalesce(sum(l.quantity), 0) - coalesce((select sum(r.quantity) "
                + "from carbon_market.retirements r join carbon_market.credit_lots x on x.id = r.lot_id where x.deleted_at is null and r.status <> 'cancelled'), 0) "
                + "as available from carbon_market.credit_lots l where l.deleted_at is null");
        Long retired = jdbc.queryForObject("select coalesce(sum(quantity), 0) from carbon_market.retirements where status = 'retired' and claimed_year = ?",
                Long.class, year);
        return new Overview(year, ((Number) held.get("held")).longValue(), ((Number) held.get("available")).longValue(), retired == null ? 0 : retired,
                pricing.offsetting(year), registryStatus(), exchangeStatus());
    }
}
