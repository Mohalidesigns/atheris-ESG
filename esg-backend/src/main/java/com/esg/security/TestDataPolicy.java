package com.esg.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Phase 3 (7): test-data hygiene. With {@code esg.testing.test-data-marking} on (local and test profiles only), a
 * request carrying {@code X-ESG-Test-Run} marks the rows it creates {@code is_test} (V025), and those rows can be
 * purged per tenant (TestDataController). Off by default; the application refuses to start with it under staging or
 * prod, so a production request can never mark or purge anything.
 */
@Component
public class TestDataPolicy {

    public static final String HEADER = "X-ESG-Test-Run";
    private static final Pattern RUN = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");
    private static final Logger log = LoggerFactory.getLogger(TestDataPolicy.class);

    private final boolean enabled;

    public TestDataPolicy(@Value("${esg.testing.test-data-marking:false}") boolean enabled, Environment env) {
        this.enabled = enabled;
        if (enabled) {
            if (Arrays.stream(env.getActiveProfiles()).anyMatch(p -> p.equals("prod") || p.equals("staging"))) {
                throw new IllegalStateException("esg.testing.test-data-marking must not be enabled in staging or prod");
            }
            log.info("Test-data marking is on: requests with {} mark the rows they create as test data", HEADER);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /** The test run named by the request, or null (marking off, no header, or a malformed value). */
    public String runOf(HttpServletRequest request) {
        if (!enabled) {
            return null;
        }
        String run = request.getHeader(HEADER);
        return run != null && RUN.matcher(run).matches() ? run : null;
    }
}
