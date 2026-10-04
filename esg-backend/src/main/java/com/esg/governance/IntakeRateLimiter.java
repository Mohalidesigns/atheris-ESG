package com.esg.governance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Throttles the public whistleblower endpoints (WP-34, D30) and supplier questionnaire links (WP-31) without recording who called: the client address is hashed
 * with a per-process random salt, kept only in memory for the window, and never logged or stored.
 */
@Component
public class IntakeRateLimiter {

    /** Submissions per client per hour, and status checks per client per hour. */
    static final int SUBMISSIONS = 5;
    static final int STATUS_CHECKS = 30;
    /** WP-31: calls to a supplier questionnaire link per client per hour (answering saves often). */
    static final int SUPPLIER_CALLS = 300;
    static final Duration WINDOW = Duration.ofHours(1);

    private final byte[] salt = new java.security.SecureRandom().generateSeed(16);
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public IntakeRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** True if this client may make another call of this kind to this tenant's channel now (and counts it). */
    public boolean allow(String kind, String tenant, String clientAddress) {
        int limit = "submit".equals(kind) ? SUBMISSIONS : "supplier".equals(kind) ? SUPPLIER_CALLS : STATUS_CHECKS;
        Instant now = clock.instant();
        Deque<Instant> q = hits.computeIfAbsent(kind + ":" + tenant + ":" + hash(clientAddress), k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(WINDOW))) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }

    private String hash(String address) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            d.update(salt);
            return HexFormat.of().formatHex(d.digest((address == null ? "" : address).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
