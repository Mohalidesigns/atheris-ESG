package com.esg.carbon;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Sandbox satellite adapter: no satellite feed is connected, so it reports exactly that — it never invents a detection
 * (CLAUDE.md rules 9 and 10). The UI labels its results "Sandbox".
 */
@Component
public class SandboxSatelliteFlareVerifier implements SatelliteFlareVerifier {

    @Override
    public String provider() {
        return "Sandbox";
    }

    @Override
    public Map<String, Object> check(String facilityName, String countryCode, LocalDate from, LocalDate to) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", provider());
        result.put("status", "not_connected");
        result.put("message", "Sandbox: no satellite feed is connected, so " + facilityName + " can't be checked for "
                + from + " – " + to + ". Nothing was detected or verified.");
        result.put("checkedAt", Instant.now().toString());
        return result;
    }
}
