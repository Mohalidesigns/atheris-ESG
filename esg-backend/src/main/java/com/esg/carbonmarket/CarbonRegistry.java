package com.esg.carbonmarket;

import java.util.Map;

/**
 * Carbon credit registry adapter (TRD §8.11 ACMIIntegration; CLAUDE.md rule 9): checks that a serial block exists and
 * is held or retired as recorded. A real registry (ACMI, Verra, Gold Standard) plugs in here.
 */
public interface CarbonRegistry {

    String name();

    boolean sandbox();

    Map<String, Object> verify(String standard, String registryProjectId, String serialKey, long serialFrom, long serialTo);
}
