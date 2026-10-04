package com.esg.carbonmarket;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Sandbox registry: nothing is connected, so it verifies nothing and says so (rules 9 and 10). Labelled "Sandbox" in the UI. */
@Component
public class SandboxCarbonRegistry implements CarbonRegistry {

    @Override
    public String name() {
        return "Sandbox";
    }

    @Override
    public boolean sandbox() {
        return true;
    }

    @Override
    public Map<String, Object> verify(String standard, String registryProjectId, String serialKey, long serialFrom, long serialTo) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("registry", name());
        r.put("sandbox", true);
        r.put("status", "not_connected");
        r.put("message", "Sandbox: no carbon registry (ACMI, Verra, Gold Standard) is connected, so serials " + serialFrom + " to " + serialTo + " of "
                + serialKey + " can't be checked here. Attach the registry statement to verify the holding.");
        r.put("checkedAt", Instant.now().toString());
        return r;
    }
}
