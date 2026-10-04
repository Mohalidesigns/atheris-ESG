package com.esg.security;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * WP-54 (D42): creates the first platform administrator of a new installation from ESG_BOOTSTRAP_ADMIN_* variables
 * (auth.bootstrap_platform). Does nothing once any platform administrator exists, so the variables can stay set; they
 * should still be removed after the first start. The administrator sets up two-factor sign-in at first login.
 */
@Component
public class PlatformBootstrap {

    private static final Logger log = LoggerFactory.getLogger(PlatformBootstrap.class);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;
    private final String organisation;
    private final String country;
    private final String currency;

    public PlatformBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder, PasswordPolicy policy,
                             @Value("${esg.bootstrap.admin-email:}") String email, @Value("${esg.bootstrap.admin-password:}") String password,
                             @Value("${esg.bootstrap.admin-first-name:Platform}") String firstName,
                             @Value("${esg.bootstrap.admin-last-name:Administrator}") String lastName,
                             @Value("${esg.bootstrap.organisation:Platform operator}") String organisation,
                             @Value("${esg.bootstrap.country:NG}") String country, @Value("${esg.bootstrap.currency:NGN}") String currency) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.policy = policy;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.organisation = organisation;
        this.country = country;
        this.currency = currency;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        run();
    }

    /** The new administrator's id, or null when nothing was done (not configured, or a platform administrator exists). */
    public UUID run() {
        if (email.isBlank() || password.isBlank()) {
            return null;
        }
        List<String> problems = policy.violations(password, email);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("ESG_BOOTSTRAP_ADMIN_PASSWORD needs " + String.join(", ", problems));
        }
        UUID id = jdbc.queryForObject("select auth.bootstrap_platform(?, ?, ?, ?, ?, ?, ?)", UUID.class, organisation, country, currency, email.trim(),
                firstName, lastName, encoder.encode(password));
        if (id == null) {
            log.info("Platform bootstrap skipped: a platform administrator already exists (remove ESG_BOOTSTRAP_ADMIN_* variables)");
        } else {
            log.warn("Platform administrator {} created; sign in, set up two-factor sign-in, then remove ESG_BOOTSTRAP_ADMIN_* variables", email);
        }
        return id;
    }
}
