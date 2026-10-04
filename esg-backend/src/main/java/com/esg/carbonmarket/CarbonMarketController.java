package com.esg.carbonmarket;

import com.esg.carbonmarket.CarbonMarketDtos.ActionRequest;
import com.esg.carbonmarket.CarbonMarketDtos.Exposure;
import com.esg.carbonmarket.CarbonMarketDtos.Lot;
import com.esg.carbonmarket.CarbonMarketDtos.LotRequest;
import com.esg.carbonmarket.CarbonMarketDtos.Offsetting;
import com.esg.carbonmarket.CarbonMarketDtos.Overview;
import com.esg.carbonmarket.CarbonMarketDtos.Price;
import com.esg.carbonmarket.CarbonMarketDtos.PriceRequest;
import com.esg.carbonmarket.CarbonMarketDtos.Project;
import com.esg.carbonmarket.CarbonMarketDtos.ProjectRequest;
import com.esg.carbonmarket.CarbonMarketDtos.RetireRequest;
import com.esg.carbonmarket.CarbonMarketDtos.Retirement;
import com.esg.carbonmarket.CarbonMarketDtos.Settings;
import com.esg.carbonmarket.CarbonMarketDtos.SettingsRequest;
import com.esg.exception.FieldValidationException;
import com.esg.model.dto.ApiResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
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

/**
 * M11 Carbon Market (WP-41, D36; TRD §6.4.2 Carbon Mkt): offset projects, the credit portfolio, retirements, internal
 * carbon prices and offset monitoring. Registry and exchange are Sandbox adapters.
 */
@RestController
@RequestMapping("/v1/carbon-market")
public class CarbonMarketController {

    private final CreditService credits;
    private final PricingService pricing;
    private final CarbonMarketInsights insights;
    private final CarbonExchange exchange;
    private final Clock clock;

    public CarbonMarketController(CreditService credits, PricingService pricing, CarbonMarketInsights insights, CarbonExchange exchange, Clock clock) {
        this.credits = credits;
        this.pricing = pricing;
        this.insights = insights;
        this.exchange = exchange;
        this.clock = clock;
    }

    private int year(Integer year) {
        int now = LocalDate.now(clock.withZone(CreditService.LAGOS)).getYear();
        int y = year == null ? now : year;
        if (y < 2000 || y > now + 1) {
            throw FieldValidationException.of("year", "must be between 2000 and next year");
        }
        return y;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('carbon_market:read')")
    public ResponseEntity<ApiResponse<Overview>> overview(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(insights.overview(year(year))));
    }

    // ---------------------------------------------------------------- projects (TRD GET /carbon-market/projects)

    @GetMapping("/projects")
    @PreAuthorize("hasAuthority('carbon_market:read')")
    public ResponseEntity<ApiResponse<List<Project>>> projects() {
        return ResponseEntity.ok(ApiResponse.ok(credits.projects()));
    }

    @GetMapping("/projects/{id}")
    @PreAuthorize("hasAuthority('carbon_market:read')")
    public ResponseEntity<ApiResponse<Project>> project(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(credits.project(id)));
    }

    @PostMapping("/projects")
    @PreAuthorize("hasAuthority('carbon_market:create')")
    public ResponseEntity<ApiResponse<Project>> createProject(@Valid @RequestBody ProjectRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(credits.createProject(r)));
    }

    @PutMapping("/projects/{id}")
    @PreAuthorize("hasAuthority('carbon_market:update')")
    public ResponseEntity<ApiResponse<Project>> updateProject(@PathVariable UUID id, @Valid @RequestBody ProjectRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(credits.updateProject(id, r)));
    }

    @DeleteMapping("/projects/{id}")
    @PreAuthorize("hasAuthority('carbon_market:update')")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        credits.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    /** MarketplaceConnector: exchange listings (Sandbox: none, and it says so). */
    @GetMapping("/listings")
    @PreAuthorize("hasAuthority('carbon_market:read')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listings(@RequestParam(required = false, defaultValue = "") String q) {
        return ResponseEntity.ok(ApiResponse.ok(exchange.listings(q.length() > 100 ? q.substring(0, 100) : q)));
    }

    // ---------------------------------------------------------------- credits (TRD GET /carbon-market/credits)

    @GetMapping("/credits")
    @PreAuthorize("hasAuthority('carbon_market:read')")
    public ResponseEntity<ApiResponse<List<Lot>>> lots(@RequestParam(required = false) UUID projectId) {
        return ResponseEntity.ok(ApiResponse.ok(credits.lots(projectId)));
    }

    @GetMapping("/credits/{id}")
    @PreAuthorize("hasAuthority('carbon_market:read')")
    public ResponseEntity<ApiResponse<Lot>> lot(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(credits.lot(id)));
    }

    @PostMapping("/credits")
    @PreAuthorize("hasAuthority('carbon_market:create')")
    public ResponseEntity<ApiResponse<Lot>> createLot(@Valid @RequestBody LotRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(credits.createLot(r)));
    }

    @PutMapping("/credits/{id}")
    @PreAuthorize("hasAuthority('carbon_market:update')")
    public ResponseEntity<ApiResponse<Lot>> updateLot(@PathVariable UUID id, @Valid @RequestBody LotRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(credits.updateLot(id, r)));
    }

    @DeleteMapping("/credits/{id}")
    @PreAuthorize("hasAuthority('carbon_market:update')")
    public ResponseEntity<Void> deleteLot(@PathVariable UUID id) {
        credits.deleteLot(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/credits/{id}/verify")
    @PreAuthorize("hasAuthority('carbon_market:approve')")
    public ResponseEntity<ApiResponse<Lot>> verify(@PathVariable UUID id, @Valid @RequestBody ActionRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(credits.verifyLot(id, r)));
    }

    @GetMapping("/credits/{id}/registry-check")
    @PreAuthorize("hasAuthority('carbon_market:read')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registryCheck(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(credits.registryCheck(id)));
    }

    // ---------------------------------------------------------------- retirements (TRD POST /carbon-market/retire)

    @PostMapping("/retire")
    @PreAuthorize("hasAuthority('carbon_market:create')")
    public ResponseEntity<ApiResponse<Retirement>> retire(@Valid @RequestBody RetireRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(credits.request(r)));
    }

    @GetMapping("/retirements")
    @PreAuthorize("hasAuthority('carbon_market:read')")
    public ResponseEntity<ApiResponse<List<Retirement>>> retirements(@RequestParam(required = false) Integer year, @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(credits.retirements(year == null ? null : year(year), status)));
    }

    @GetMapping("/retirements/{id}")
    @PreAuthorize("hasAuthority('carbon_market:read')")
    public ResponseEntity<ApiResponse<Retirement>> retirement(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(credits.retirement(id)));
    }

    /** approve (carbon_market:approve, not the requester) or cancel (carbon_market:create) — checked by the service. */
    @PostMapping("/retirements/{id}/actions")
    @PreAuthorize("hasAuthority('carbon_market:read')")
    public ResponseEntity<ApiResponse<Retirement>> act(@PathVariable UUID id, @Valid @RequestBody ActionRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(credits.act(id, r)));
    }

    // ---------------------------------------------------------------- pricing and offsetting

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('carbon_market:read')")
    public ResponseEntity<ApiResponse<Settings>> settings() {
        return ResponseEntity.ok(ApiResponse.ok(pricing.settings()));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('carbon_market:approve')")
    public ResponseEntity<ApiResponse<Settings>> updateSettings(@Valid @RequestBody SettingsRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(pricing.updateSettings(r)));
    }

    @GetMapping("/internal-prices")
    @PreAuthorize("hasAuthority('carbon_market:read')")
    public ResponseEntity<ApiResponse<List<Price>>> prices() {
        return ResponseEntity.ok(ApiResponse.ok(pricing.prices()));
    }

    @GetMapping("/internal-prices/{id}")
    @PreAuthorize("hasAuthority('carbon_market:read')")
    public ResponseEntity<ApiResponse<Price>> price(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(pricing.price(id)));
    }

    @PostMapping("/internal-prices")
    @PreAuthorize("hasAuthority('carbon_market:approve')")
    public ResponseEntity<ApiResponse<Price>> createPrice(@Valid @RequestBody PriceRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(pricing.createPrice(r)));
    }

    @PutMapping("/internal-prices/{id}")
    @PreAuthorize("hasAuthority('carbon_market:approve')")
    public ResponseEntity<ApiResponse<Price>> updatePrice(@PathVariable UUID id, @Valid @RequestBody PriceRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(pricing.updatePrice(id, r)));
    }

    @DeleteMapping("/internal-prices/{id}")
    @PreAuthorize("hasAuthority('carbon_market:approve')")
    public ResponseEntity<Void> deletePrice(@PathVariable UUID id) {
        pricing.deletePrice(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/internal-prices/exposure")
    @PreAuthorize("hasAuthority('carbon_market:read') and hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<Exposure>> exposure(@RequestParam(required = false) Integer year,
                                                          @RequestParam(required = false, defaultValue = "shadow") String priceType) {
        return ResponseEntity.ok(ApiResponse.ok(pricing.exposure(year(year), priceType)));
    }

    @GetMapping("/offsetting")
    @PreAuthorize("hasAuthority('carbon_market:read')")
    public ResponseEntity<ApiResponse<Offsetting>> offsetting(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(pricing.offsetting(year(year))));
    }
}
