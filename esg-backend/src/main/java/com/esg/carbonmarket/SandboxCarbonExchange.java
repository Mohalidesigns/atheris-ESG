package com.esg.carbonmarket;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Sandbox exchange: no African carbon exchange is connected, so there are no listings (rules 9 and 10). */
@Component
public class SandboxCarbonExchange implements CarbonExchange {

    @Override
    public String name() {
        return "Sandbox";
    }

    @Override
    public boolean sandbox() {
        return true;
    }

    @Override
    public Map<String, Object> listings(String query) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("exchange", name());
        r.put("sandbox", true);
        r.put("status", "not_connected");
        r.put("message", "Sandbox: no carbon exchange is connected, so no listings are shown. Record credits you have bought from your registry account.");
        r.put("listings", CarbonExchange.none());
        return r;
    }
}
