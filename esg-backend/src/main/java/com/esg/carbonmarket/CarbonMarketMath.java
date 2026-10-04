package com.esg.carbonmarket;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Carbon market arithmetic (WP-41, D36 (4)–(6)), free of the database so the golden values can be tested on their own
 * (CarbonMarketMathTest): free serial blocks, offset share, offset-to-reduction ratio and the over-reliance flag.
 */
public final class CarbonMarketMath {
    private CarbonMarketMath() {}

    /** An inclusive serial range already used in a lot (retired or reserved). */
    public record Range(long from, long to) {}

    /**
     * The first contiguous block of {@code quantity} free serials in [lotFrom, lotTo], given the used ranges; null when no
     * single gap is large enough (the caller says how many are free in total).
     */
    public static Range nextFree(long lotFrom, long lotTo, List<Range> used, long quantity) {
        long cursor = lotFrom;
        for (Range r : used.stream().sorted((a, b) -> Long.compare(a.from(), b.from())).toList()) {
            if (r.from() - cursor >= quantity) {
                return new Range(cursor, cursor + quantity - 1);
            }
            cursor = Math.max(cursor, r.to() + 1);
        }
        return lotTo - cursor + 1 >= quantity ? new Range(cursor, cursor + quantity - 1) : null;
    }

    /** Serials still free in the lot. */
    public static long free(long lotFrom, long lotTo, List<Range> used) {
        long taken = used.stream().mapToLong(r -> Math.min(r.to(), lotTo) - Math.max(r.from(), lotFrom) + 1).filter(n -> n > 0).sum();
        return lotTo - lotFrom + 1 - taken;
    }

    /** Credits as a percentage of gross emissions, one decimal; null when there are no emissions. */
    public static BigDecimal share(BigDecimal credits, BigDecimal gross) {
        if (gross == null || gross.signum() <= 0) {
            return null;
        }
        return credits.multiply(BigDecimal.valueOf(100)).divide(gross, 1, RoundingMode.HALF_UP);
    }

    /** Credits per tonne actually reduced, two decimals; null when nothing was reduced (any credit is then over-reliance). */
    public static BigDecimal offsetToReduction(BigDecimal credits, BigDecimal reductions) {
        if (reductions == null || reductions.signum() <= 0) {
            return null;
        }
        return credits.divide(reductions, 2, RoundingMode.HALF_UP);
    }

    /** Over-reliance: credits outweigh real reductions (or there are none), or their share passes the tenant's ceiling. */
    public static boolean overReliance(BigDecimal credits, BigDecimal reductions, BigDecimal share, BigDecimal ceilingPct) {
        if (credits.signum() <= 0) {
            return false;
        }
        boolean outweigh = reductions == null || reductions.signum() <= 0 || credits.compareTo(reductions) > 0;
        boolean overCeiling = share != null && share.compareTo(ceilingPct) > 0;
        return outweigh || overCeiling;
    }

    /** Net emissions shown beside gross: never below zero. */
    public static BigDecimal net(BigDecimal gross, BigDecimal credits) {
        BigDecimal n = gross.subtract(credits);
        return n.signum() < 0 ? BigDecimal.ZERO.setScale(gross.scale()) : n;
    }
}
