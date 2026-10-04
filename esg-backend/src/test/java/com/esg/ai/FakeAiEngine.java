package com.esg.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test double for the Python AI service (tests only, CLAUDE.md rule 2): records what the backend sent and answers in
 * the service's shape. The real algorithms are tested in esg-ai/tests and HttpAiEngineTest covers the HTTP contract.
 */
public class FakeAiEngine implements AiEngine {

    /** The value the fake Isolation Forest isolates. */
    static final double SPIKE = 987654.321;

    static final ObjectMapper JSON = new ObjectMapper();
    static volatile boolean reachable = true;
    static volatile boolean llm = true;
    static final AtomicReference<List<SeriesPoint>> lastSeries = new AtomicReference<>();
    static final AtomicReference<JsonNode> extraction = new AtomicReference<>();
    static final AtomicReference<RuntimeException> extractionFailure = new AtomicReference<>();
    static final List<String> extractedFiles = new ArrayList<>();

    static void reset() {
        reachable = true;
        llm = true;
        extraction.set(null);
        extractionFailure.set(null);
    }

    @Override
    public Status status() {
        return new Status(true, reachable, reachable && llm, reachable ? "Fake AI service" : "The AI service isn't reachable", null);
    }

    private void up() {
        if (!reachable) {
            throw new AiUnavailable("The AI service isn't reachable.");
        }
    }

    @Override
    public JsonNode forecast(List<SeriesPoint> series, String frequency, int horizon, double level) {
        up();
        lastSeries.set(series);
        long n = series.stream().dropWhile(p -> p.value() == 0).count();
        if (n < ("month".equals(frequency) ? 6 : 3)) {
            throw new AiRefused("at least " + ("month".equals(frequency) ? 6 : 3) + " " + frequency + "s of data are needed; there are " + n);
        }
        ObjectNode out = JSON.createObjectNode().put("method", "linear_trend").put("model", "OLS linear trend").put("n", n).put("level", level)
                .put("engine", "fake statsmodels");
        ArrayNode points = out.putArray("points");
        double last = series.get(series.size() - 1).value();
        int year = Integer.parseInt(series.get(series.size() - 1).period().substring(0, 4));
        for (int i = 1; i <= horizon; i++) {
            points.addObject().put("period", String.valueOf(year + i)).put("value", last).put("low", last * 0.9).put("high", last * 1.1);
        }
        out.putObject("backtest").put("holdout", 1).put("mape", 2.5).put("method", "linear_trend");
        out.putArray("warnings");
        return out;
    }

    @Override
    public JsonNode isolationForest(List<ScoredPoint> points, double contamination) {
        up();
        ObjectNode out = JSON.createObjectNode().put("engine", "fake IsolationForest").put("contamination", contamination);
        ArrayNode scores = out.putArray("scores");
        for (ScoredPoint p : points) {
            boolean spike = p.value() == SPIKE;
            scores.addObject().put("id", p.id()).put("score", spike ? 1.0 : 0.1).put("flagged", spike).put("zscore", spike ? 42.5 : 0.1).put("change", 0);
        }
        return out;
    }

    @Override
    public JsonNode extract(byte[] pdf, String fileName, List<MetricRef> metrics) {
        up();
        synchronized (extractedFiles) {
            extractedFiles.add(fileName + ":" + pdf.length + ":" + metrics.size());
        }
        if (extractionFailure.get() != null) {
            throw extractionFailure.get();
        }
        return extraction.get();
    }
}
