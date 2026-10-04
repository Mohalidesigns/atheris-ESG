package com.esg.notifications;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SMS channel via the Africa's Talking messaging API (TRD §6.6.3). Points at the <strong>sandbox</strong> by default and
 * is labelled "Sandbox" in the UI (CLAUDE.md rule 9). Without an API key, deliveries are recorded as skipped.
 */
@Component
public class SmsSender {

    private final String username;
    private final String apiKey;
    private final String url;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SmsSender(@Value("${esg.notifications.sms.africastalking-username}") String username,
                     @Value("${esg.notifications.sms.africastalking-api-key:}") String apiKey,
                     @Value("${esg.notifications.sms.africastalking-url}") String url) {
        this.username = username;
        this.apiKey = apiKey;
        this.url = url;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean sandbox() {
        return url.contains("sandbox");
    }

    public SendResult send(String to, String message) {
        if (!configured()) {
            return SendResult.notConfigured("SMS is not configured (Africa's Talking API key unset)");
        }
        String form = "username=" + enc(username) + "&to=" + enc(to) + "&message=" + enc(message);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("apiKey", apiKey).header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() / 100 == 2 ? SendResult.sent()
                    : SendResult.failed("HTTP " + response.statusCode());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return SendResult.failed(e.getClass().getSimpleName());
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
