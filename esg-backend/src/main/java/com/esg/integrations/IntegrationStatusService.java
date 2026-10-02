package com.esg.integrations;

import com.esg.carbon.SatelliteFlareVerifier;
import com.esg.files.VirusScanner;
import com.esg.notifications.EmailSender;
import com.esg.notifications.SmsSender;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Settings › Integrations (Phase 2, item 11): whether each external service is connected, a sandbox, or not configured.
 * Read from the adapters themselves (CLAUDE.md rule 9), so the panel can't claim a connection that isn't there.
 */
@Service
public class IntegrationStatusService {

    /** status: connected | sandbox | not_configured. */
    public record Integration(String key, String name, String status, String detail, String usedFor) {}

    private final SatelliteFlareVerifier satellite;
    private final VirusScanner scanner;
    private final EmailSender email;
    private final SmsSender sms;

    public IntegrationStatusService(SatelliteFlareVerifier satellite, VirusScanner scanner, EmailSender email, SmsSender sms) {
        this.satellite = satellite;
        this.scanner = scanner;
        this.email = email;
        this.sms = sms;
    }

    public List<Integration> list() {
        boolean satSandbox = "Sandbox".equals(satellite.provider());
        return List.of(
                new Integration("satellite_flaring", "Satellite flaring check", satSandbox ? "sandbox" : "connected",
                        satSandbox ? "No satellite feed is connected; checks say so and never invent a detection" : satellite.provider(),
                        "Carbon › Gas flaring: cross-checks reported flare volumes"),
                new Integration("regulator_feed", "Regulatory change feed", "not_configured",
                        "No regulator feed is connected; changes are recorded by hand", "Compliance › Regulatory changes"),
                new Integration("virus_scanner", "Virus scanner", scanner.sandbox() ? "sandbox" : "connected", scanner.description(),
                        "Every uploaded file is scanned before it can be downloaded"),
                new Integration("email", "Email", email.configured() ? "connected" : "not_configured",
                        email.configured() ? "SMTP server configured" : "No SMTP server configured (spring.mail.host); in-app notifications still work",
                        "Notifications: reminders, approvals, account changes"),
                new Integration("sms", "SMS (Africa's Talking)", !sms.configured() ? "not_configured" : sms.sandbox() ? "sandbox" : "connected",
                        !sms.configured() ? "No Africa's Talking API key configured" : sms.sandbox() ? "Africa's Talking sandbox" : "Africa's Talking live",
                        "Notifications by SMS for users who choose it"));
    }
}
