package com.esg.risk;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Adapter for regional climate hazard data (TRD §8.5 AfricaClimateModels: flooding probabilities, desertification
 * indices, heat stress days; CLAUDE.md rule 9). A real implementation (e.g. a climate data service for Sub-Saharan
 * Africa) plugs in here; its result is a suggestion the assessor cites, never a score set by itself.
 */
public interface ClimateHazardProvider {

    /** Name shown in the UI; the sandbox says "Sandbox". */
    String provider();

    boolean sandbox();

    Map<String, Object> lookup(String siteName, BigDecimal latitude, BigDecimal longitude, String hazard, String pathway, int horizonYear);
}
