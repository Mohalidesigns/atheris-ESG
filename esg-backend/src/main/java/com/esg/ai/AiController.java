package com.esg.ai;

import com.esg.ai.AiDtos.AcceptRequest;
import com.esg.ai.AiDtos.DismissRequest;
import com.esg.ai.AiDtos.ExtractedValue;
import com.esg.ai.AiDtos.Extraction;
import com.esg.ai.AiDtos.ExtractionRequest;
import com.esg.ai.AiDtos.ForecastRequest;
import com.esg.ai.AiDtos.RejectRequest;
import com.esg.ai.AiDtos.Run;
import com.esg.ai.AiDtos.ScanRequest;
import com.esg.ai.AiDtos.ScanResult;
import com.esg.ai.AiDtos.Status;
import com.esg.model.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WP-50 Grounded AI (D38; TRD §8.9, TRD GET /analytics/predictions): forecasts, the Isolation Forest scan and report
 * extraction. Every result is an AI run: labelled, traceable to its engine and inputs, and reviewed by a person.
 */
@RestController
@RequestMapping("/v1/analytics")
public class AiController {

    private final AiEngine engine;
    private final AiRunService runs;
    private final ForecastService forecasts;
    private final MlAnomalyService ml;
    private final ExtractionService extractions;

    public AiController(AiEngine engine, AiRunService runs, ForecastService forecasts, MlAnomalyService ml, ExtractionService extractions) {
        this.engine = engine;
        this.runs = runs;
        this.forecasts = forecasts;
        this.ml = ml;
        this.extractions = extractions;
    }

    @GetMapping("/ai/status")
    @PreAuthorize("hasAnyAuthority('analytics:read', 'data_points:create')")
    public ResponseEntity<ApiResponse<Status>> status() {
        AiEngine.Status s = engine.status();
        return ResponseEntity.ok(ApiResponse.ok(new Status(s.configured(), s.reachable(), s.llmAvailable(), s.detail())));
    }

    // ---------------------------------------------------------------- forecasts

    @GetMapping("/predictions")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<List<Run>>> predictions() {
        return ResponseEntity.ok(ApiResponse.ok(runs.list("forecast")));
    }

    @GetMapping("/predictions/{id}")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<Run>> prediction(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(runs.get(id, "forecast")));
    }

    @PostMapping("/predictions")
    @PreAuthorize("hasAuthority('analytics:create')")
    public ResponseEntity<ApiResponse<Run>> forecast(@Valid @RequestBody ForecastRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(forecasts.create(r)));
    }

    @PostMapping("/predictions/{id}/dismiss")
    @PreAuthorize("hasAuthority('analytics:create')")
    public ResponseEntity<ApiResponse<Run>> dismissPrediction(@PathVariable UUID id, @Valid @RequestBody DismissRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(runs.dismiss(id, "forecast", r)));
    }

    // ---------------------------------------------------------------- Isolation Forest

    @PostMapping("/ml-anomalies/scan")
    @PreAuthorize("hasAuthority('analytics:create')")
    public ResponseEntity<ApiResponse<ScanResult>> scan(@Valid @RequestBody ScanRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(ml.scan(r)));
    }

    @GetMapping("/ml-anomalies/runs")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<List<Run>>> scans() {
        return ResponseEntity.ok(ApiResponse.ok(runs.list("isolation_forest")));
    }

    // ---------------------------------------------------------------- extraction

    @GetMapping("/extractions")
    @PreAuthorize("hasAuthority('data_points:create')")
    public ResponseEntity<ApiResponse<List<Run>>> extractions() {
        return ResponseEntity.ok(ApiResponse.ok(runs.list("extraction")));
    }

    @PostMapping("/extractions")
    @PreAuthorize("hasAuthority('data_points:create')")
    public ResponseEntity<ApiResponse<Run>> extract(@Valid @RequestBody ExtractionRequest r) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(extractions.start(r.fileId()), "Reading the report"));
    }

    @GetMapping("/extractions/{id}")
    @PreAuthorize("hasAuthority('data_points:create')")
    public ResponseEntity<ApiResponse<Extraction>> extraction(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(extractions.get(id)));
    }

    @PostMapping("/extractions/{id}/values/{valueId}/accept")
    @PreAuthorize("hasAuthority('data_points:create')")
    public ResponseEntity<ApiResponse<ExtractedValue>> accept(@PathVariable UUID id, @PathVariable UUID valueId, @Valid @RequestBody AcceptRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(extractions.accept(id, valueId, r), "Saved as a draft data point"));
    }

    @PostMapping("/extractions/{id}/values/{valueId}/reject")
    @PreAuthorize("hasAuthority('data_points:create')")
    public ResponseEntity<ApiResponse<ExtractedValue>> reject(@PathVariable UUID id, @PathVariable UUID valueId, @Valid @RequestBody RejectRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(extractions.reject(id, valueId, r)));
    }
}
