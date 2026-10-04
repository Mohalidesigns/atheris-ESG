package com.esg.risk;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Sandbox climate data adapter: no regional climate data source is connected, so it says exactly that and never invents
 * a hazard figure (CLAUDE.md rules 9 and 10). The UI labels it "Sandbox"; assessors enter scores with their own source.
 */
@Component
public class SandboxClimateHazardProvider implements ClimateHazardProvider {

    @Override
    public String provider() {
        return "Sandbox";
    }

    @Override
    public boolean sandbox() {
        return true;
    }

    @Override
    public Map<String, Object> lookup(String siteName, BigDecimal latitude, BigDecimal longitude, String hazard, String pathway, int horizonYear) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", provider());
        result.put("sandbox", true);
        result.put("status", "not_connected");
        result.put("message", "Sandbox: no regional climate data source is connected, so no " + hazard.replace('_', ' ') + " figure for "
                + siteName + " (" + pathway + ", " + horizonYear + ") can be suggested. Enter the score with the source you used.");
        result.put("checkedAt", Instant.now().toString());
        return result;
    }
}
