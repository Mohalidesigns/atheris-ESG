package com.esg.carbon;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * TRD §8.2 FinancedEmissionsCalculator, PCAF Global GHG Accounting and Reporting Standard Part A (2nd ed., 2022):
 * financed emissions = attribution factor × counterparty emissions, with the attribution factor
 * <ul>
 *   <li>listed equity & corporate bonds: outstanding ÷ EVIC;</li>
 *   <li>business loans & unlisted equity: outstanding ÷ (total equity + debt), or EVIC for listed companies;</li>
 *   <li>project finance: outstanding ÷ (total project equity + debt);</li>
 *   <li>commercial real estate and mortgages: outstanding ÷ property value at origination;</li>
 *   <li>motor vehicle loans: outstanding ÷ vehicle value at origination.</li>
 * </ul>
 * The denominator the user enters is the one for the asset class; outstanding can't exceed it (factor ≤ 1).
 */
public final class PcafCalculator {

    private PcafCalculator() {}

    public static BigDecimal attribution(BigDecimal outstanding, BigDecimal denominator) {
        return outstanding.divide(denominator, MathContext.DECIMAL128).setScale(10, RoundingMode.HALF_UP);
    }

    public static BigDecimal financed(BigDecimal attribution, BigDecimal counterpartyEmissions) {
        return attribution.multiply(counterpartyEmissions, MathContext.DECIMAL128).setScale(6, RoundingMode.HALF_UP);
    }
}
