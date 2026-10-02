package com.esg.datahub;

import com.esg.datahub.DataHubDtos.DataSourceRequest;
import com.esg.datahub.DataHubDtos.DataSourceResponse;
import com.esg.datahub.DataHubDtos.FxRateRequest;
import com.esg.datahub.DataHubDtos.FxRateResponse;
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

/** Data Hub set-up: data sources and exchange rates (WP-20). */
@RestController
@RequestMapping("/v1/data")
public class DataHubSetupController {

    private final DataSourceService sources;
    private final FxRateService rates;

    public DataHubSetupController(DataSourceService sources, FxRateService rates) {
        this.sources = sources;
        this.rates = rates;
    }

    @GetMapping("/sources")
    @PreAuthorize("hasAuthority('data_sources:read')")
    public ResponseEntity<ApiResponse<List<DataSourceResponse>>> sources(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.ok(sources.list(activeOnly)));
    }

    /** Phase 3 (8): who can be named as the responsible person on a data point or source. */
    @GetMapping("/owners")
    @PreAuthorize("hasAnyAuthority('data_points:read', 'data_sources:read')")
    public ResponseEntity<ApiResponse<List<DataSourceService.Person>>> owners() {
        return ResponseEntity.ok(ApiResponse.ok(sources.people()));
    }

    @GetMapping("/sources/{id}")
    @PreAuthorize("hasAuthority('data_sources:read')")
    public ResponseEntity<ApiResponse<DataSourceResponse>> source(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(sources.get(id)));
    }

    @PostMapping("/sources")
    @PreAuthorize("hasAuthority('data_sources:manage')")
    public ResponseEntity<ApiResponse<DataSourceResponse>> createSource(@Valid @RequestBody DataSourceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(sources.create(request), "Data source added"));
    }

    @PutMapping("/sources/{id}")
    @PreAuthorize("hasAuthority('data_sources:manage')")
    public ResponseEntity<ApiResponse<DataSourceResponse>> updateSource(@PathVariable UUID id, @Valid @RequestBody DataSourceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(sources.update(id, request), "Data source updated"));
    }

    @GetMapping("/fx-rates")
    @PreAuthorize("hasAuthority('fx_rates:read')")
    public ResponseEntity<ApiResponse<List<FxRateResponse>>> rates(@RequestParam(required = false) String from,
                                                                  @RequestParam(required = false) String to) {
        return ResponseEntity.ok(ApiResponse.ok(rates.list(from, to)));
    }

    @GetMapping("/fx-rates/{id}")
    @PreAuthorize("hasAuthority('fx_rates:read')")
    public ResponseEntity<ApiResponse<FxRateResponse>> rate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(rates.get(id)));
    }

    @PostMapping("/fx-rates")
    @PreAuthorize("hasAuthority('fx_rates:manage')")
    public ResponseEntity<ApiResponse<FxRateResponse>> createRate(@Valid @RequestBody FxRateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(rates.create(request), "Exchange rate added"));
    }

    @PutMapping("/fx-rates/{id}")
    @PreAuthorize("hasAuthority('fx_rates:manage')")
    public ResponseEntity<ApiResponse<FxRateResponse>> updateRate(@PathVariable UUID id, @Valid @RequestBody FxRateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(rates.update(id, request), "Exchange rate updated"));
    }

    @DeleteMapping("/fx-rates/{id}")
    @PreAuthorize("hasAuthority('fx_rates:manage')")
    public ResponseEntity<Void> deleteRate(@PathVariable UUID id) {
        rates.delete(id);
        return ResponseEntity.noContent().build();
    }
}
