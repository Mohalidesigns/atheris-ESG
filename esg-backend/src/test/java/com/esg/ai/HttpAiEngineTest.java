package com.esg.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The HTTP contract with esg-ai (D38 (1)): token, JSON and multipart bodies, refusals (422) and outages (503, down). */
class HttpAiEngineTest {

  private HttpServer server;
  private final Map<String, String> seen = new ConcurrentHashMap<>();
  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/health", ex -> reply(ex, 200, "{\"llm\":\"granite4:micro (Ollama)\",\"llmAvailable\":true}"));
    server.createContext("/v1/forecast", ex -> {
      seen.put("token", String.valueOf(ex.getRequestHeaders().getFirst("X-AI-Token")));
      String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      seen.put("forecast", body);
      if (body.contains("\"horizon\":99")) {
        reply(ex, 422, "{\"detail\":\"horizon must be 1 to 36\",\"errorCode\":\"ESG-422-AI-INPUT\"}");
      } else if (body.contains("\"horizon\":98")) {
        reply(ex, 500, "{\"detail\":\"boom\"}");
      } else {
        reply(ex, 200, "{\"method\":\"linear_trend\",\"points\":[]}");
      }
    });
    server.createContext("/v1/extract", ex -> {
      seen.put("extractType", String.valueOf(ex.getRequestHeaders().getFirst("Content-Type")));
      seen.put("extract", new String(ex.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
      reply(ex, 503, "{\"detail\":\"the language model could not be reached\"}");
    });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
    byte[] b = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json");
    ex.sendResponseHeaders(status, b.length);
    ex.getResponseBody().write(b);
    ex.close();
  }

  private HttpAiEngine engine(String url) {
    return new HttpAiEngine(url, "s3cret", 30, json);
  }

  @Test
  void callsTheServiceWithItsTokenAndMapsItsAnswers() {
    HttpAiEngine e = engine("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    AiEngine.Status s = e.status();
    assertThat(s.configured()).isTrue();
    assertThat(s.reachable()).isTrue();
    assertThat(s.llmAvailable()).isTrue();
    assertThat(s.detail()).contains("granite4:micro (Ollama)");

    JsonNode out = e.forecast(List.of(new AiEngine.SeriesPoint("2024", 10)), "year", 2, 0.8);
    assertThat(out.path("method").asText()).isEqualTo("linear_trend");
    assertThat(seen.get("token")).isEqualTo("s3cret");
    assertThat(seen.get("forecast")).contains("\"period\":\"2024\"").contains("\"frequency\":\"year\"").contains("\"level\":0.8");

    assertThatThrownBy(() -> e.forecast(List.of(), "year", 99, 0.8)).isInstanceOf(AiEngine.AiRefused.class).hasMessage("horizon must be 1 to 36");
    assertThatThrownBy(() -> e.forecast(List.of(), "year", 98, 0.8)).isInstanceOf(AiEngine.AiUnavailable.class).hasMessageContaining("boom");
    assertThatThrownBy(() -> e.extract("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), "report.pdf", List.of(new AiEngine.MetricRef("k", "Name", "kWh"))))
        .isInstanceOf(AiEngine.AiUnavailable.class).hasMessageContaining("the language model could not be reached");
    assertThat(seen.get("extractType")).startsWith("multipart/form-data");
    assertThat(seen.get("extract")).contains("filename=\"report.pdf\"").contains("%PDF-1.7").contains("[{\"key\":\"k\",\"name\":\"Name\",\"unit\":\"kWh\"}]");
  }

  @Test
  void notConfiguredOrDownIsSaidPlainly() {
    AiEngine none = engine("");
    assertThat(none.status().configured()).isFalse();
    assertThat(none.status().detail()).isEqualTo("No AI service configured (esg.ai.url)");
    assertThatThrownBy(() -> none.isolationForest(List.of(), 0.05)).isInstanceOf(AiEngine.AiUnavailable.class)
        .hasMessage("No AI service is configured for this installation.");

    int port = server.getAddress().getPort();
    server.stop(0);
    AiEngine down = engine("http://127.0.0.1:" + port);
    assertThat(down.status().reachable()).isFalse();
    assertThatThrownBy(() -> down.isolationForest(List.of(), 0.05)).isInstanceOf(AiEngine.AiUnavailable.class)
        .hasMessage("The AI service isn't reachable.");
  }
}
