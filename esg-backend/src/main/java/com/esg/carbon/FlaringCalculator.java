package com.esg.carbon;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TRD §8.2 GasFlaringCalculator: volume × gas composition × combustion efficiency × emission factor, by combustion
 * stoichiometry (API Compendium of GHG Emissions Methodologies for the Oil and Natural Gas Industry, flare combustion):
 * <pre>
 *   n      = V (Mcf) × 1000 ÷ 379.3 scf/lb-mol           (standard conditions 60 °F, 14.696 psia)
 *   CO2    = n × (CE × Σ yᵢ·Cᵢ + y_CO2) × 44.01 lb        (hydrocarbons burned, plus CO2 already in the gas)
 *   CH4    = n × y_CH4 × (1 − CE) × 16.043 lb             (unburned methane slip)
 *   CO2e   = CO2 + CH4 × GWP_CH4                         (tonnes; N2O from flares is negligible and omitted)
 * </pre>
 * yᵢ are mole fractions, Cᵢ carbon atoms (C5+ taken as 5). Combustion efficiency defaults to 98% (D22).
 */
public final class FlaringCalculator {

    static final BigDecimal SCF_PER_LBMOL = new BigDecimal("379.3");
    static final BigDecimal MW_CO2 = new BigDecimal("44.01");
    static final BigDecimal MW_CH4 = new BigDecimal("16.043");
    static final BigDecimal KG_PER_LB = new BigDecimal("0.45359237");
    static final BigDecimal DEFAULT_EFFICIENCY = new BigDecimal("0.98");
    private static final MathContext MC = MathContext.DECIMAL128;

    /** Composition components (mole %) and their carbon atoms; co2 and nitrogen carry no combustible carbon. */
    static final Map<String, Integer> CARBON_ATOMS = carbonAtoms();

    private FlaringCalculator() {}

    public record Result(BigDecimal co2Tonnes, BigDecimal ch4Tonnes, BigDecimal co2eTonnes) {}

    private static Map<String, Integer> carbonAtoms() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("methane", 1);
        m.put("ethane", 2);
        m.put("propane", 3);
        m.put("butane", 4);
        m.put("pentane_plus", 5);
        m.put("co2", 0);
        m.put("nitrogen", 0);
        return m;
    }

    /** @param composition mole % by component (keys of {@link #CARBON_ATOMS}); must total 100 ± 0.5 (checked by the caller) */
    public static Result calculate(BigDecimal volumeMcf, Map<String, BigDecimal> composition, BigDecimal efficiency,
                                   BigDecimal gwpCh4) {
        BigDecimal hundred = BigDecimal.valueOf(100);
        BigDecimal moles = volumeMcf.multiply(BigDecimal.valueOf(1000), MC).divide(SCF_PER_LBMOL, MC);
        BigDecimal carbon = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> c : CARBON_ATOMS.entrySet()) {
            BigDecimal y = composition.getOrDefault(c.getKey(), BigDecimal.ZERO).divide(hundred, MC);
            carbon = carbon.add(y.multiply(BigDecimal.valueOf(c.getValue()), MC));
        }
        BigDecimal yCo2 = composition.getOrDefault("co2", BigDecimal.ZERO).divide(hundred, MC);
        BigDecimal yCh4 = composition.getOrDefault("methane", BigDecimal.ZERO).divide(hundred, MC);
        BigDecimal co2Lb = moles.multiply(efficiency.multiply(carbon, MC).add(yCo2), MC).multiply(MW_CO2, MC);
        BigDecimal ch4Lb = moles.multiply(yCh4, MC).multiply(BigDecimal.ONE.subtract(efficiency), MC).multiply(MW_CH4, MC);
        BigDecimal co2 = co2Lb.multiply(KG_PER_LB, MC).divide(BigDecimal.valueOf(1000), MC);
        BigDecimal ch4 = ch4Lb.multiply(KG_PER_LB, MC).divide(BigDecimal.valueOf(1000), MC);
        return new Result(EmissionEngine.round(co2), EmissionEngine.round(ch4),
                EmissionEngine.round(co2.add(ch4.multiply(gwpCh4, MC))));
    }
}
