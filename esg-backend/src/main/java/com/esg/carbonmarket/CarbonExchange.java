package com.esg.carbonmarket;

import java.util.List;
import java.util.Map;

/** African carbon exchange adapter (TRD §8.11 MarketplaceConnector; CLAUDE.md rule 9): listings to discover credits. */
public interface CarbonExchange {

    String name();

    boolean sandbox();

    /** Listings matching the query; a sandbox returns none and says why. */
    Map<String, Object> listings(String query);

    static List<Map<String, Object>> none() {
        return List.of();
    }
}
