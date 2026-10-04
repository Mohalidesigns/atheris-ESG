package com.esg.risk;

import com.esg.model.dto.ApiResponse;
import com.esg.risk.RiskDtos.ActionRequest;
import com.esg.risk.RiskDtos.Assessment;
import com.esg.risk.RiskDtos.AssessmentRequest;
import com.esg.risk.RiskDtos.CarbonPriceRequest;
import com.esg.risk.RiskDtos.Heatmap;
import com.esg.risk.RiskDtos.Overview;
import com.esg.risk.RiskDtos.Person;
import com.esg.risk.RiskDtos.Physical;
import com.esg.risk.RiskDtos.PhysicalRequest;
import com.esg.risk.RiskDtos.Risk;
import com.esg.risk.RiskDtos.RiskRequest;
import com.esg.risk.RiskDtos.Scenario;
import com.esg.risk.RiskDtos.TopicRequest;
import com.esg.risk.RiskDtos.TransitionAnalysis;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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

/** M5 Risk & Materiality (WP-32; TRD §6.4.2 Risk): double materiality, risk register and heatmaps, climate scenarios. */
@RestController
@RequestMapping("/v1/risk")
public class RiskController {

    private final MaterialityService materiality;
    private final RiskRegisterService register;
    private final ClimateService climate;
    private final RiskInsights insights;
    private final RiskSupport support;

    public RiskController(MaterialityService materiality, RiskRegisterService register, ClimateService climate, RiskInsights insights,
                          RiskSupport support) {
        this.materiality = materiality;
        this.register = register;
        this.climate = climate;
        this.insights = insights;
        this.support = support;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('risk:read')")
    public ResponseEntity<ApiResponse<Overview>> overview() {
        return ResponseEntity.ok(ApiResponse.ok(insights.overview()));
    }

    @GetMapping("/people")
    @PreAuthorize("hasAuthority('risk:read')")
    public ResponseEntity<ApiResponse<List<Person>>> people() {
        return ResponseEntity.ok(ApiResponse.ok(support.holders("update")));
    }

    // ---------------------------------------------------------------- materiality

    @GetMapping("/assessments")
    @PreAuthorize("hasAuthority('risk:read')")
    public ResponseEntity<ApiResponse<List<Assessment>>> assessments(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(materiality.list(year)));
    }

    @GetMapping("/assessments/{id}")
    @PreAuthorize("hasAuthority('risk:read')")
    public ResponseEntity<ApiResponse<Assessment>> assessment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(materiality.get(id)));
    }

    @PostMapping("/assessments")
    @PreAuthorize("hasAuthority('risk:create')")
    public ResponseEntity<ApiResponse<Assessment>> addAssessment(@Valid @RequestBody AssessmentRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(materiality.create(r), "Assessment created"));
    }

    @PutMapping("/assessments/{id}")
    @PreAuthorize("hasAuthority('risk:update')")
    public ResponseEntity<ApiResponse<Assessment>> updateAssessment(@PathVariable UUID id, @Valid @RequestBody AssessmentRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(materiality.update(id, r), "Assessment updated"));
    }

    @DeleteMapping("/assessments/{id}")
    @PreAuthorize("hasAuthority('risk:update')")
    public ResponseEntity<Void> deleteAssessment(@PathVariable UUID id, @RequestParam long version) {
        materiality.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/assessments/{id}/actions")
    @PreAuthorize("hasAuthority('risk:update') or hasAuthority('risk:approve')")
    public ResponseEntity<ApiResponse<Assessment>> assessmentAction(@PathVariable UUID id, @Valid @RequestBody ActionRequest a) {
        return ResponseEntity.ok(ApiResponse.ok(materiality.act(id, a)));
    }

    @PostMapping("/assessments/{id}/topics")
    @PreAuthorize("hasAuthority('risk:update')")
    public ResponseEntity<ApiResponse<Assessment>> addTopic(@PathVariable UUID id, @Valid @RequestBody TopicRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(materiality.addTopic(id, r), "Topic added"));
    }

    @PutMapping("/assessments/{id}/topics/{topicId}")
    @PreAuthorize("hasAuthority('risk:update')")
    public ResponseEntity<ApiResponse<Assessment>> updateTopic(@PathVariable UUID id, @PathVariable UUID topicId, @Valid @RequestBody TopicRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(materiality.updateTopic(id, topicId, r), "Topic updated"));
    }

    @DeleteMapping("/assessments/{id}/topics/{topicId}")
    @PreAuthorize("hasAuthority('risk:update')")
    public ResponseEntity<ApiResponse<Assessment>> deleteTopic(@PathVariable UUID id, @PathVariable UUID topicId, @RequestParam long version) {
        return ResponseEntity.ok(ApiResponse.ok(materiality.deleteTopic(id, topicId, version), "Topic removed"));
    }

    @GetMapping("/topics")
    @PreAuthorize("hasAuthority('risk:read')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> topics() {
        return ResponseEntity.ok(ApiResponse.ok(materiality.linkableTopics()));
    }

    // ---------------------------------------------------------------- register

    @GetMapping("/risks")
    @PreAuthorize("hasAuthority('risk:read')")
    public ResponseEntity<ApiResponse<List<Risk>>> risks(@RequestParam(required = false) String category,
                                                         @RequestParam(required = false) String status, @RequestParam(required = false) UUID orgId) {
        return ResponseEntity.ok(ApiResponse.ok(register.list(category, status, orgId)));
    }

    @GetMapping("/risks/{id}")
    @PreAuthorize("hasAuthority('risk:read')")
    public ResponseEntity<ApiResponse<Risk>> risk(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(register.get(id)));
    }

    @PostMapping("/risks")
    @PreAuthorize("hasAuthority('risk:create')")
    public ResponseEntity<ApiResponse<Risk>> addRisk(@Valid @RequestBody RiskRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(register.create(r), "Risk added"));
    }

    @PutMapping("/risks/{id}")
    @PreAuthorize("hasAuthority('risk:update')")
    public ResponseEntity<ApiResponse<Risk>> updateRisk(@PathVariable UUID id, @Valid @RequestBody RiskRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(register.update(id, r), "Risk updated"));
    }

    @DeleteMapping("/risks/{id}")
    @PreAuthorize("hasAuthority('risk:update')")
    public ResponseEntity<Void> deleteRisk(@PathVariable UUID id, @RequestParam long version) {
        register.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/heatmap")
    @PreAuthorize("hasAuthority('risk:read')")
    public ResponseEntity<ApiResponse<Heatmap>> heatmap(@RequestParam(defaultValue = "portfolio") String level,
                                                        @RequestParam(defaultValue = "false") boolean residual,
                                                        @RequestParam(required = false) UUID orgId) {
        return ResponseEntity.ok(ApiResponse.ok(register.heatmap(level, residual, orgId)));
    }

    // ---------------------------------------------------------------- climate

    @GetMapping("/scenarios")
    @PreAuthorize("hasAuthority('risk:read')")
    public ResponseEntity<ApiResponse<List<Scenario>>> scenarios() {
        return ResponseEntity.ok(ApiResponse.ok(climate.scenarios()));
    }

    @PutMapping("/scenarios/prices")
    @PreAuthorize("hasAuthority('risk:update')")
    public ResponseEntity<ApiResponse<List<Scenario>>> setPrice(@Valid @RequestBody CarbonPriceRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(climate.setPrice(r), "Carbon price saved"));
    }

    @DeleteMapping("/scenarios/{code}/prices/{year}")
    @PreAuthorize("hasAuthority('risk:update')")
    public ResponseEntity<ApiResponse<List<Scenario>>> deletePrice(@PathVariable String code, @PathVariable int year) {
        return ResponseEntity.ok(ApiResponse.ok(climate.deletePrice(code, year), "Carbon price removed"));
    }

    @GetMapping("/scenarios/transition")
    @PreAuthorize("hasAuthority('risk:read') and hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<TransitionAnalysis>> transition(@RequestParam(required = false) Integer baselineYear) {
        return ResponseEntity.ok(ApiResponse.ok(climate.transition(baselineYear)));
    }

    @GetMapping("/physical")
    @PreAuthorize("hasAuthority('risk:read')")
    public ResponseEntity<ApiResponse<List<Physical>>> physical(@RequestParam(required = false) UUID orgId) {
        return ResponseEntity.ok(ApiResponse.ok(climate.physical(orgId)));
    }

    @GetMapping("/physical/{id}")
    @PreAuthorize("hasAuthority('risk:read')")
    public ResponseEntity<ApiResponse<Physical>> physicalOne(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(climate.getPhysical(id)));
    }

    @PostMapping("/physical")
    @PreAuthorize("hasAuthority('risk:create')")
    public ResponseEntity<ApiResponse<Physical>> addPhysical(@Valid @RequestBody PhysicalRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(climate.createPhysical(r), "Physical risk recorded"));
    }

    @PutMapping("/physical/{id}")
    @PreAuthorize("hasAuthority('risk:update')")
    public ResponseEntity<ApiResponse<Physical>> updatePhysical(@PathVariable UUID id, @Valid @RequestBody PhysicalRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(climate.updatePhysical(id, r), "Physical risk updated"));
    }

    @DeleteMapping("/physical/{id}")
    @PreAuthorize("hasAuthority('risk:update')")
    public ResponseEntity<Void> deletePhysical(@PathVariable UUID id, @RequestParam long version) {
        climate.deletePhysical(id, version);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/physical/data-source")
    @PreAuthorize("hasAuthority('risk:read')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dataSource() {
        return ResponseEntity.ok(ApiResponse.ok(climate.dataSource()));
    }

    @GetMapping("/physical/suggest")
    @PreAuthorize("hasAuthority('risk:read')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> suggest(@RequestParam UUID orgId, @RequestParam String hazard,
                                                                    @RequestParam(defaultValue = "RCP4.5") String pathway,
                                                                    @RequestParam(defaultValue = "2050") int horizonYear) {
        return ResponseEntity.ok(ApiResponse.ok(climate.suggest(orgId, hazard, pathway, horizonYear)));
    }
}
