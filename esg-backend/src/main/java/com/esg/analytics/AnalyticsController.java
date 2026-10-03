package com.esg.analytics;

import com.esg.analytics.AnalyticsDtos.ActionRequest;
import com.esg.analytics.AnalyticsDtos.Anomaly;
import com.esg.analytics.AnalyticsDtos.AssessRequest;
import com.esg.analytics.AnalyticsDtos.BenchmarkDataset;
import com.esg.analytics.AnalyticsDtos.BenchmarkDatasetRequest;
import com.esg.analytics.AnalyticsDtos.Comparison;
import com.esg.analytics.AnalyticsDtos.InternalBenchmark;
import com.esg.analytics.AnalyticsDtos.Kpi;
import com.esg.analytics.AnalyticsDtos.KpiRequest;
import com.esg.analytics.AnalyticsDtos.Maturity;
import com.esg.analytics.AnalyticsDtos.MaturitySnapshot;
import com.esg.analytics.AnalyticsDtos.Overview;
import com.esg.analytics.AnalyticsDtos.ScanRequest;
import com.esg.analytics.AnalyticsDtos.ScanResult;
import com.esg.analytics.AnalyticsDtos.SdgAlignment;
import com.esg.analytics.AnalyticsDtos.SdgPriorityRequest;
import com.esg.analytics.AnalyticsDtos.Settings;
import com.esg.analytics.AnalyticsDtos.SettingsRequest;
import com.esg.model.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * M9 Analytics (WP-35, D34; TRD §6.4.2 Analytics): anomaly flags and variance explanations, custom KPIs, benchmarks,
 * the ESG maturity index and SDG alignment. Rules and statistics only; predictions are WP-50.
 */
@RestController
@RequestMapping("/v1/analytics")
public class AnalyticsController {

    private final AnomalyService anomalies;
    private final KpiService kpis;
    private final BenchmarkService benchmarks;
    private final MaturityService maturity;
    private final SdgService sdg;
    private final AnalyticsInsights insights;
    private final AnalyticsSupport support;

    public AnalyticsController(AnomalyService anomalies, KpiService kpis, BenchmarkService benchmarks, MaturityService maturity, SdgService sdg,
                               AnalyticsInsights insights, AnalyticsSupport support) {
        this.anomalies = anomalies;
        this.kpis = kpis;
        this.benchmarks = benchmarks;
        this.maturity = maturity;
        this.sdg = sdg;
        this.insights = insights;
        this.support = support;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<Overview>> overview(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(insights.overview(support.year(year))));
    }

    // ---------------------------------------------------------------- settings

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<Settings>> settings() {
        return ResponseEntity.ok(ApiResponse.ok(anomalies.settings()));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('analytics:configure')")
    public ResponseEntity<ApiResponse<Settings>> updateSettings(@Valid @RequestBody SettingsRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(anomalies.updateSettings(r)));
    }

    // ---------------------------------------------------------------- anomalies

    @GetMapping("/anomalies")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<List<Anomaly>>> anomalies(@RequestParam(required = false) Integer year, @RequestParam(required = false) String status,
                                                                @RequestParam(required = false) String kind, @RequestParam(required = false) UUID orgId,
                                                                @RequestParam(required = false) String metric) {
        return ResponseEntity.ok(ApiResponse.ok(anomalies.list(year == null ? null : support.year(year), status, kind, orgId, metric)));
    }

    @GetMapping("/anomalies/{id}")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<Anomaly>> anomaly(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(anomalies.get(id)));
    }

    @PostMapping("/anomalies/scan")
    @PreAuthorize("hasAuthority('analytics:create')")
    public ResponseEntity<ApiResponse<ScanResult>> scan(@Valid @RequestBody(required = false) ScanRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(anomalies.scan(support.year(r == null ? null : r.year()))));
    }

    /** explain (analytics:create); accept, return, dismiss (data_points:approve) — checked per action by the service. */
    @PostMapping("/anomalies/{id}/actions")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<Anomaly>> act(@PathVariable UUID id, @Valid @RequestBody ActionRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(anomalies.act(id, r)));
    }

    // ---------------------------------------------------------------- KPIs

    @GetMapping("/kpis")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<List<Kpi>>> kpis(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(kpis.list(status)));
    }

    @GetMapping("/kpis/{id}")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<Kpi>> kpi(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(kpis.get(id)));
    }

    @PostMapping("/kpis")
    @PreAuthorize("hasAuthority('analytics:create')")
    public ResponseEntity<ApiResponse<Kpi>> createKpi(@Valid @RequestBody KpiRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(kpis.create(r)));
    }

    @PutMapping("/kpis/{id}")
    @PreAuthorize("hasAuthority('analytics:create')")
    public ResponseEntity<ApiResponse<Kpi>> updateKpi(@PathVariable UUID id, @Valid @RequestBody KpiRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(kpis.update(id, r)));
    }

    @DeleteMapping("/kpis/{id}")
    @PreAuthorize("hasAuthority('analytics:create')")
    public ResponseEntity<Void> deleteKpi(@PathVariable UUID id) {
        kpis.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- benchmarks

    @GetMapping("/benchmarks")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<List<Comparison>>> benchmarks(@RequestParam(required = false) Integer year, @RequestParam(required = false) String metric) {
        return ResponseEntity.ok(ApiResponse.ok(benchmarks.compare(support.year(year), metric)));
    }

    @GetMapping("/benchmarks/internal")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<InternalBenchmark>> internal(@RequestParam String metric, @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(benchmarks.internal(metric, support.year(year))));
    }

    @GetMapping("/benchmark-datasets")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<List<BenchmarkDataset>>> datasets() {
        return ResponseEntity.ok(ApiResponse.ok(benchmarks.list()));
    }

    @GetMapping("/benchmark-datasets/{id}")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<BenchmarkDataset>> dataset(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(benchmarks.get(id)));
    }

    @PostMapping("/benchmark-datasets")
    @PreAuthorize("hasAuthority('analytics:configure')")
    public ResponseEntity<ApiResponse<BenchmarkDataset>> createDataset(@Valid @RequestBody BenchmarkDatasetRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(benchmarks.create(r)));
    }

    @PutMapping("/benchmark-datasets/{id}")
    @PreAuthorize("hasAuthority('analytics:configure')")
    public ResponseEntity<ApiResponse<BenchmarkDataset>> updateDataset(@PathVariable UUID id, @Valid @RequestBody BenchmarkDatasetRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(benchmarks.update(id, r)));
    }

    @DeleteMapping("/benchmark-datasets/{id}")
    @PreAuthorize("hasAuthority('analytics:configure')")
    public ResponseEntity<Void> deleteDataset(@PathVariable UUID id) {
        benchmarks.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- maturity

    @GetMapping("/maturity")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<Maturity>> maturity(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(maturity.compute(support.year(year))));
    }

    @GetMapping("/maturity/assessments")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<List<MaturitySnapshot>>> assessments() {
        return ResponseEntity.ok(ApiResponse.ok(maturity.history()));
    }

    @PostMapping("/maturity/assessments")
    @PreAuthorize("hasAuthority('analytics:create')")
    public ResponseEntity<ApiResponse<MaturitySnapshot>> assess(@Valid @RequestBody AssessRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(maturity.assess(r)));
    }

    // ---------------------------------------------------------------- SDG alignment

    @GetMapping("/sdg-alignment")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<ApiResponse<SdgAlignment>> sdgAlignment(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(sdg.alignment(support.year(year))));
    }

    @PutMapping("/sdg-priorities")
    @PreAuthorize("hasAuthority('analytics:create')")
    public ResponseEntity<ApiResponse<SdgAlignment>> setPriority(@Valid @RequestBody SdgPriorityRequest r, @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(sdg.setPriority(r, support.year(year))));
    }

    @DeleteMapping("/sdg-priorities/{goal}")
    @PreAuthorize("hasAuthority('analytics:create')")
    public ResponseEntity<ApiResponse<SdgAlignment>> removePriority(@PathVariable int goal, @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(sdg.removePriority(goal, support.year(year))));
    }
}
