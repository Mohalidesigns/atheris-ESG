package com.esg.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * WP-52 (D40 (6)): staging and production refuse to start with a development secret or an unauthenticated AI service.
 * Secrets come from the environment (a secrets manager in deployment); the values committed for local use and tests
 * are recognisable, so a copy-pasted local configuration can't reach a real environment.
 */
@Component
public class ProductionSecretsPolicy {

    /** Markers in the committed local/test values (application-local.yml, application-test.yml, CI). */
    static final List<String> DEV_MARKERS = List.of("local-dev", "test-only", "dev-only", "esg_app_local", "esg_owner_local", "e2e-ai-token",
            "ci-ai-token", "YS0bCdEHVNXWPBZwaz5BaQrw7vGZoHjF+yTlWjZMnFw=", "S5gZx/LBue1vcvNZoRCnZrSAJI+G1tekOSj6v5Is6Y0=");
    static final List<String> SECRETS = List.of("esg.security.jwt-secret", "esg.security.data-encryption-key", "spring.datasource.password",
            "esg.ai.token");

    public ProductionSecretsPolicy(Environment env) {
        if (Arrays.stream(env.getActiveProfiles()).noneMatch(p -> p.equals("prod") || p.equals("staging"))) {
            return;
        }
        List<String> problems = problems(name -> env.getProperty(name, ""));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start: " + String.join("; ", problems));
        }
    }

    static List<String> problems(java.util.function.Function<String, String> property) {
        List<String> out = new ArrayList<>();
        for (String name : SECRETS) {
            String value = property.apply(name);
            if (DEV_MARKERS.stream().anyMatch(value::contains)) {
                out.add(name + " is a development value");
            }
        }
        if (!property.apply("esg.ai.url").isBlank() && property.apply("esg.ai.token").length() < 24) {
            out.add("esg.ai.token must be set (at least 24 characters) when esg.ai.url is");
        }
        return out;
    }
}
