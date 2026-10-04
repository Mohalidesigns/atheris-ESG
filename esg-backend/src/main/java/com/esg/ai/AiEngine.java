package com.esg.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * The AI service behind an adapter (CLAUDE.md rule 9; D12, D38): forecasting, Isolation Forest scoring and grounded
 * extraction. Implementations never invent an answer: when the service isn't configured or reachable they throw
 * {@link AiUnavailable} and the caller says so.
 */
public interface AiEngine {

    /** configured: a URL is set; reachable: the service answered; llmAvailable: its language model is loaded. */
    record Status(boolean configured, boolean reachable, boolean llmAvailable, String detail, JsonNode health) {}

    record SeriesPoint(String period, double value) {}

    record ScoredPoint(String id, String group, String seq, double value) {}

    record MetricRef(String key, String name, String unit) {}

    /** Raised when the AI service can't be used (not configured, down, or its model unavailable): HTTP 503. */
    class AiUnavailable extends RuntimeException {
        public AiUnavailable(String message) {
            super(message);
        }
    }

    /** Raised when the service refused the input (too little data, unreadable PDF): HTTP 422 with its reason. */
    class AiRefused extends RuntimeException {
        public AiRefused(String message) {
            super(message);
        }
    }

    Status status();

    JsonNode forecast(List<SeriesPoint> series, String frequency, int horizon, double level);

    JsonNode isolationForest(List<ScoredPoint> points, double contamination);

    JsonNode extract(byte[] pdf, String fileName, List<MetricRef> metrics);

    static Map<String, Object> describe(Status s) {
        return Map.of("configured", s.configured(), "reachable", s.reachable(), "llmAvailable", s.llmAvailable(), "detail", s.detail());
    }
}
