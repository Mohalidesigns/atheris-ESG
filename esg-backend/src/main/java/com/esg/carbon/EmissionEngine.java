package com.esg.carbon;

import com.esg.reference.ReferenceDtos.EmissionFactor;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * TRD §8.2 EmissionCalculationEngine: CO2e = activity data × emission factor × GWP.
 * <ul>
 *   <li>A factor with per-gas values (kg CO2, CH4, N2O per unit) is weighted with the tenant's GWP set:
 *       CO2e = A × (EF_CO2 + EF_CH4 × GWP_CH4 + EF_N2O × GWP_N2O) ÷ 1000, and the gases are kept;</li>
 *   <li>a factor published as CO2e is applied as published: CO2e = A × EF ÷ 1000 (its own GWP basis).</li>
 * </ul>
 * Tonnes are rounded half-up to 6 decimals at the end (full precision in between).
 */
public final class EmissionEngine {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal KG_PER_TONNE = BigDecimal.valueOf(1000);

    private EmissionEngine() {}

    public record Gwp(String set, BigDecimal ch4, BigDecimal n2o) {}

    /** {@code gwpSet} is the set applied, or the factor's own (possibly null) for CO2e factors. */
    public record Result(BigDecimal co2eTonnes, BigDecimal co2Tonnes, BigDecimal ch4Tonnes, BigDecimal n2oTonnes, String gwpSet,
                         boolean perGas) {}

    public static Result calculate(BigDecimal activity, EmissionFactor f, Gwp gwp) {
        if (f.co2Factor() != null && f.ch4Factor() != null && f.n2oFactor() != null) {
            BigDecimal co2 = activity.multiply(f.co2Factor(), MC).divide(KG_PER_TONNE, MC);
            BigDecimal ch4 = activity.multiply(f.ch4Factor(), MC).divide(KG_PER_TONNE, MC);
            BigDecimal n2o = activity.multiply(f.n2oFactor(), MC).divide(KG_PER_TONNE, MC);
            BigDecimal co2e = co2.add(ch4.multiply(gwp.ch4(), MC)).add(n2o.multiply(gwp.n2o(), MC));
            return new Result(round(co2e), round(co2), round(ch4), round(n2o), gwp.set(), true);
        }
        BigDecimal co2e = activity.multiply(f.factorValue(), MC).divide(KG_PER_TONNE, MC);
        return new Result(round(co2e), null, null, null, f.gwpSet(), false);
    }

    static BigDecimal round(BigDecimal v) {
        return v.setScale(6, RoundingMode.HALF_UP);
    }
}
