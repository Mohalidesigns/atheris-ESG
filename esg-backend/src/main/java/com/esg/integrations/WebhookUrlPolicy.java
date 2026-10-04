package com.esg.integrations;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * WP-46 (D44): where webhooks may be sent. Production: HTTPS to a public address only — never loopback, private,
 * link-local or metadata addresses (server-side request forgery). Checked when an endpoint is saved and again before
 * every delivery (the name may resolve elsewhere later). Development and tests may allow plain HTTP and local addresses.
 */
@Component
public class WebhookUrlPolicy {

    private final boolean allowHttp;
    private final boolean allowPrivate;

    public WebhookUrlPolicy(@Value("${esg.webhooks.allow-http:false}") boolean allowHttp,
                            @Value("${esg.webhooks.allow-private-networks:false}") boolean allowPrivate) {
        this.allowHttp = allowHttp;
        this.allowPrivate = allowPrivate;
    }

    /** Why the URL isn't allowed, or empty when it is. */
    public Optional<String> problem(String url) {
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            return Optional.of("is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        if (!"https".equals(scheme) && !(allowHttp && "http".equals(scheme))) {
            return Optional.of("must start with https://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return Optional.of("must include a host name");
        }
        if (uri.getUserInfo() != null) {
            return Optional.of("must not contain a user name or password");
        }
        if (allowPrivate) {
            return Optional.empty();
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress() || address.isMulticastAddress() || uniqueLocal(address)) {
                    return Optional.of("must be a public address (private and local networks are not allowed)");
                }
            }
        } catch (UnknownHostException e) {
            return Optional.of("host name can't be found");
        }
        return Optional.empty();
    }

    /** IPv6 unique local addresses (fc00::/7) aren't covered by isSiteLocalAddress. */
    private static boolean uniqueLocal(InetAddress address) {
        byte[] b = address.getAddress();
        return b.length == 16 && (b[0] & 0xfe) == 0xfc;
    }
}
