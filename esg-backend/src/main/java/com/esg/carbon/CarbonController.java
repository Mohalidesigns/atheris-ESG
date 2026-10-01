package com.esg.carbon;

import com.esg.carbon.CarbonDtos.EmissionRequest;
import com.esg.carbon.CarbonDtos.EmissionResponse;
import com.esg.carbon.CarbonDtos.FinancedRequest;
import com.esg.carbon.CarbonDtos.FinancedResponse;
import com.esg.carbon.CarbonDtos.FinancedSummary;
import com.esg.carbon.CarbonDtos.FlaringRequest;
import com.esg.carbon.CarbonDtos.FlaringResponse;
import com.esg.carbon.CarbonDtos.InstrumentRequest;
import com.esg.carbon.CarbonDtos.InstrumentResponse;
import com.esg.carbon.CarbonDtos.Recalculation;
import com.esg.carbon.CarbonDtos.Summary;
import com.esg.carbon.CarbonDtos.TargetRequest;
import com.esg.carbon.CarbonDtos.TargetResponse;
import com.esg.controller.SortableFields;
import com.esg.exception.FieldValidationException;
import com.esg.model.dto.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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

/** M2 Carbon Accounting (WP-21, TRD §6.4.2 carbon endpoints and §8.2). */
@RestController
@RequestMapping("/v1/carbon")
public class CarbonController {

    static final Set<String> SORTABLE = Set.of("createdAt", "reportingPeriodStart", "scope", "sourceActivity", "co2eTonnes");

    private final EmissionService emissions;
    private final FlaringService flaring;
    private final InstrumentService instruments;
    private final TargetService targets;
    private final FinancedService financed;
    private final JdbcTemplate jdbc;

    public CarbonController(EmissionService emissions, FlaringService flaring, InstrumentService instruments, TargetService targets,
                            FinancedService financed, JdbcTemplate jdbc) {
        this.emissions = emissions;
        this.flaring = flaring;
        this.instruments = instruments;
        this.targets = targets;
        this.financed = financed;
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- emission records

    @GetMapping("/emissions")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<Page<EmissionResponse>>> list(
            @RequestParam(required = false) String scope, @RequestParam(required = false) String source,
            @RequestParam(required = false) UUID orgId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(emissions.list(blank(scope), blank(source), orgId, from, to,
                SortableFields.check(pageable, SORTABLE))));
    }

    @GetMapping("/emissions/{id}")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<EmissionResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(emissions.get(id)));
    }

    @PostMapping("/emissions")
    @PreAuthorize("hasAuthority('carbon_data:create')")
    public ResponseEntity<ApiResponse<EmissionResponse>> create(@Valid @RequestBody EmissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(emissions.create(request), "Emissions calculated"));
    }

    @PutMapping("/emissions/{id}")
    @PreAuthorize("hasAuthority('carbon_data:update')")
    public ResponseEntity<ApiResponse<EmissionResponse>> update(@PathVariable UUID id, @Valid @RequestBody EmissionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(emissions.update(id, request), "Emissions recalculated"));
    }

    @DeleteMapping("/emissions/{id}")
    @PreAuthorize("hasAuthority('carbon_data:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam Long version) {
        emissions.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/emissions/{id}/verify")
    @PreAuthorize("hasAuthority('carbon_data:approve')")
    public ResponseEntity<ApiResponse<EmissionResponse>> verify(@PathVariable UUID id, @RequestBody Map<String, Long> body) {
        return ResponseEntity.ok(ApiResponse.ok(emissions.verify(id, body == null ? null : body.get("version")), "Verified"));
    }

    @PostMapping("/recalculate")
    @PreAuthorize("hasAuthority('carbon_data:update')")
    public ResponseEntity<ApiResponse<Recalculation>> recalculate(@RequestBody Map<String, String> body) {
        LocalDate from = date(body, "from");
        LocalDate to = date(body, "to");
        if (to.isBefore(from)) {
            throw FieldValidationException.of("to", "must be on or after the start date");
        }
        return ResponseEntity.ok(ApiResponse.ok(emissions.recalculate(from, to)));
    }

    @GetMapping("/emissions/summary")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<Summary>> summary(@RequestParam(required = false) Integer year) {
        int y = year == null ? LocalDate.now().getYear() : year;
        if (y < 2000 || y > 2100) {
            throw FieldValidationException.of("year", "must be between 2000 and 2100");
        }
        return ResponseEntity.ok(ApiResponse.ok(emissions.summary(y)));
    }

    /** GWP-100 values per IPCC assessment (reference) and the set the tenant uses. */
    @GetMapping("/gwp")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> gwp() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "tenantSet", jdbc.queryForObject("select gwp_set from auth.tenant_settings", String.class),
                "values", jdbc.queryForList("select gas, gwp_set as \"gwpSet\", value, source from reference.gwp_values order by gwp_set, gas"))));
    }

    // ---------------------------------------------------------------- gas flaring

    @GetMapping("/flaring")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<List<FlaringResponse>>> flaring(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(flaring.list(year)));
    }

    @GetMapping("/flaring/{id}")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<FlaringResponse>> flaringRecord(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(flaring.get(id)));
    }

    @PostMapping("/flaring")
    @PreAuthorize("hasAuthority('carbon_data:create')")
    public ResponseEntity<ApiResponse<FlaringResponse>> createFlaring(@Valid @RequestBody FlaringRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(flaring.create(request), "Flaring emissions calculated"));
    }

    @PutMapping("/flaring/{id}")
    @PreAuthorize("hasAuthority('carbon_data:update')")
    public ResponseEntity<ApiResponse<FlaringResponse>> updateFlaring(@PathVariable UUID id, @Valid @RequestBody FlaringRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(flaring.update(id, request), "Flaring emissions recalculated"));
    }

    @DeleteMapping("/flaring/{id}")
    @PreAuthorize("hasAuthority('carbon_data:delete')")
    public ResponseEntity<Void> deleteFlaring(@PathVariable UUID id, @RequestParam Long version) {
        flaring.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/flaring/{id}/satellite-check")
    @PreAuthorize("hasAuthority('carbon_data:update')")
    public ResponseEntity<ApiResponse<FlaringResponse>> satelliteCheck(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(flaring.satelliteCheck(id)));
    }

    // ---------------------------------------------------------------- RECs / PPAs

    @GetMapping("/instruments")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<List<InstrumentResponse>>> instruments() {
        return ResponseEntity.ok(ApiResponse.ok(instruments.list()));
    }

    @GetMapping("/instruments/{id}")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<InstrumentResponse>> instrument(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(instruments.get(id)));
    }

    @PostMapping("/instruments")
    @PreAuthorize("hasAuthority('carbon_data:create')")
    public ResponseEntity<ApiResponse<InstrumentResponse>> createInstrument(@Valid @RequestBody InstrumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(instruments.create(request), "Instrument recorded"));
    }

    @PutMapping("/instruments/{id}")
    @PreAuthorize("hasAuthority('carbon_data:update')")
    public ResponseEntity<ApiResponse<InstrumentResponse>> updateInstrument(@PathVariable UUID id, @Valid @RequestBody InstrumentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(instruments.update(id, request), "Instrument updated"));
    }

    @DeleteMapping("/instruments/{id}")
    @PreAuthorize("hasAuthority('carbon_data:delete')")
    public ResponseEntity<Void> deleteInstrument(@PathVariable UUID id, @RequestParam Long version) {
        instruments.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- targets

    @GetMapping("/targets")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<List<TargetResponse>>> targets() {
        return ResponseEntity.ok(ApiResponse.ok(targets.list()));
    }

    @GetMapping("/targets/{id}")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<TargetResponse>> target(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(targets.get(id)));
    }

    @PostMapping("/targets")
    @PreAuthorize("hasAuthority('carbon_data:create')")
    public ResponseEntity<ApiResponse<TargetResponse>> createTarget(@Valid @RequestBody TargetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(targets.create(request), "Target set"));
    }

    @PutMapping("/targets/{id}")
    @PreAuthorize("hasAuthority('carbon_data:update')")
    public ResponseEntity<ApiResponse<TargetResponse>> updateTarget(@PathVariable UUID id, @Valid @RequestBody TargetRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(targets.update(id, request), "Target updated"));
    }

    @DeleteMapping("/targets/{id}")
    @PreAuthorize("hasAuthority('carbon_data:delete')")
    public ResponseEntity<Void> deleteTarget(@PathVariable UUID id, @RequestParam Long version) {
        targets.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- PCAF financed emissions

    @GetMapping("/financed")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<List<FinancedResponse>>> financed(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(financed.list(year)));
    }

    @GetMapping("/financed/summary")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<FinancedSummary>> financedSummary(@RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.ok(financed.summary(year)));
    }

    @GetMapping("/financed/{id}")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<FinancedResponse>> financedPosition(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(financed.get(id)));
    }

    @PostMapping("/financed")
    @PreAuthorize("hasAuthority('carbon_data:create')")
    public ResponseEntity<ApiResponse<FinancedResponse>> createFinanced(@Valid @RequestBody FinancedRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(financed.create(request), "Position recorded"));
    }

    @PutMapping("/financed/{id}")
    @PreAuthorize("hasAuthority('carbon_data:update')")
    public ResponseEntity<ApiResponse<FinancedResponse>> updateFinanced(@PathVariable UUID id, @Valid @RequestBody FinancedRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(financed.update(id, request), "Position updated"));
    }

    @DeleteMapping("/financed/{id}")
    @PreAuthorize("hasAuthority('carbon_data:delete')")
    public ResponseEntity<Void> deleteFinanced(@PathVariable UUID id, @RequestParam Long version) {
        financed.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    private static LocalDate date(Map<String, String> body, String key) {
        String v = body == null ? null : body.get(key);
        if (v == null || v.isBlank()) {
            throw FieldValidationException.of(key, "is required");
        }
        try {
            return LocalDate.parse(v);
        } catch (java.time.format.DateTimeParseException e) {
            throw FieldValidationException.of(key, "must be a date (YYYY-MM-DD)");
        }
    }

    private static String blank(String v) {
        return v == null || v.isBlank() || "all".equalsIgnoreCase(v) ? null : v;
    }
}
