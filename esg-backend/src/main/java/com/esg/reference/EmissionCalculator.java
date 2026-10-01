package com.esg.reference;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** TRD §8.2: CO2e (t) = activity data × emission factor (kg CO2e per unit) ÷ 1000, kept to 6 decimals. */
public final class EmissionCalculator {
    private EmissionCalculator() {}

    public static BigDecimal co2eTonnes(BigDecimal activityData, BigDecimal factorKgPerUnit) {
        return activityData.multiply(factorKgPerUnit).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }
}
