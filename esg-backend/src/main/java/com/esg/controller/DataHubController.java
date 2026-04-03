package com.esg.controller;

import com.esg.model.dto.ApiResponse;
import com.esg.model.dto.DataPointDto;
import com.esg.model.entity.DataPoint;
import com.esg.security.UserPrincipal;
import com.esg.service.DataHubService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/data")
public class DataHubController {

    public DataHubController(DataHubService dataHubService) {
        this.dataHubService = dataHubService;
    }

    private final DataHubService dataHubService;

    @GetMapping("/points")
    public ResponseEntity<ApiResponse<Page<DataPoint>>> getDataPoints(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<DataPoint> page = dataHubService.getDataPoints(category, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/points/{id}")
    public ResponseEntity<ApiResponse<DataPoint>> getDataPoint(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(dataHubService.getDataPoint(id)));
    }

    @PostMapping("/points")
    public ResponseEntity<ApiResponse<DataPoint>> createDataPoint(
            @Valid @RequestBody DataPointDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        DataPoint dp = dataHubService.createDataPoint(dto, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dp, "Data point created"));
    }

    @PutMapping("/points/{id}/validate")
    public ResponseEntity<ApiResponse<DataPoint>> validateDataPoint(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        String status = body.getOrDefault("status", "validated");
        DataPoint dp = dataHubService.validateDataPoint(id, status, principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(dp, "Data point " + status));
    }
}
