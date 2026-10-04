package com.esg.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** The Python AI service (esg-ai, FastAPI) over HTTP, authenticated with a shared service token (D38 (1)). */
@Component
public class HttpAiEngine implements AiEngine {

    private final String url;
    private final RestClient quick;
    private final RestClient slow;
    private final ObjectMapper json;

    public HttpAiEngine(@Value("${esg.ai.url:}") String url, @Value("${esg.ai.token:}") String token,
                        @Value("${esg.ai.timeout-seconds:600}") long timeoutSeconds, ObjectMapper json) {
        this.url = url == null ? "" : url.replaceAll("/+$", "");
        this.json = json;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory shortTimeout = new JdkClientHttpRequestFactory(http);
        shortTimeout.setReadTimeout(Duration.ofSeconds(120));
        JdkClientHttpRequestFactory longTimeout = new JdkClientHttpRequestFactory(http);
        longTimeout.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.quick = RestClient.builder().baseUrl(this.url.isEmpty() ? "http://ai.invalid" : this.url).requestFactory(shortTimeout)
                .defaultHeader("X-AI-Token", token == null ? "" : token).build();
        this.slow = quick.mutate().requestFactory(longTimeout).build();
    }

    @Override
    public Status status() {
        if (url.isEmpty()) {
            return new Status(false, false, false, "No AI service configured (esg.ai.url)", null);
        }
        try {
            JsonNode h = quick.get().uri("/v1/health").retrieve().body(JsonNode.class);
            boolean llm = h != null && h.path("llmAvailable").asBoolean();
            return new Status(true, true, llm, llm ? "Forecasting, Isolation Forest and extraction (" + h.path("llm").asText() + ")"
                    : "Forecasting and Isolation Forest; the language model for extraction isn't loaded", h);
        } catch (RuntimeException e) {
            return new Status(true, false, false, "The AI service isn't reachable", null);
        }
    }

    @Override
    public JsonNode forecast(List<SeriesPoint> series, String frequency, int horizon, double level) {
        return call(() -> quick.post().uri("/v1/forecast").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("series", series, "frequency", frequency, "horizon", horizon, "level", level)).retrieve().body(JsonNode.class));
    }

    @Override
    public JsonNode isolationForest(List<ScoredPoint> points, double contamination) {
        return call(() -> quick.post().uri("/v1/anomalies/isolation-forest").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("points", points, "contamination", contamination)).retrieve().body(JsonNode.class));
    }

    @Override
    public JsonNode extract(byte[] pdf, String fileName, List<MetricRef> metrics) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        try {
            form.add("metrics", json.writeValueAsString(metrics));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return call(() -> slow.post().uri("/v1/extract").contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(JsonNode.class));
    }

    private JsonNode call(java.util.function.Supplier<JsonNode> request) {
        if (url.isEmpty()) {
            throw new AiUnavailable("No AI service is configured for this installation.");
        }
        try {
            return request.get();
        } catch (HttpStatusCodeException e) {
            String detail = detail(e);
            if (e.getStatusCode().value() == 422 || e.getStatusCode().value() == 413) {
                throw new AiRefused(detail);
            }
            throw new AiUnavailable("The AI service couldn't answer: " + detail);
        } catch (ResourceAccessException e) {
            throw new AiUnavailable("The AI service isn't reachable.");
        }
    }

    private String detail(HttpStatusCodeException e) {
        try {
            return json.readTree(e.getResponseBodyAsString()).path("detail").asText(e.getStatusText());
        } catch (com.fasterxml.jackson.core.JsonProcessingException x) {
            return e.getStatusText();
        }
    }
}
