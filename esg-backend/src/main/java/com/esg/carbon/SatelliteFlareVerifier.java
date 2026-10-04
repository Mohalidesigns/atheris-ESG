package com.esg.carbon;

import java.time.LocalDate;
import java.util.Map;

/**
 * Adapter for satellite flare detection (TRD §8.2 "satellite verification integration via API"; CLAUDE.md rule 9).
 * A real implementation (e.g. a VIIRS Nightfire feed) plugs in here. Results are evidence only: they never mark a
 * record verified by themselves.
 */
public interface SatelliteFlareVerifier {

    /** Name shown in the UI; the sandbox says "Sandbox". */
    String provider();

    Map<String, Object> check(String facilityName, String countryCode, LocalDate from, LocalDate to);
}
