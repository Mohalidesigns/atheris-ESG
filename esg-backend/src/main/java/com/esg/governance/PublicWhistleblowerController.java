package com.esg.governance;

import com.esg.exception.ApiException;
import com.esg.governance.GovernanceDtos.CaseStatus;
import com.esg.governance.GovernanceDtos.Receipt;
import com.esg.governance.GovernanceDtos.StatusRequest;
import com.esg.governance.GovernanceDtos.Submission;
import com.esg.model.dto.ApiResponse;
import com.esg.security.PublicEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The anonymous whistleblower channel (TRD §6.4.2 POST /governance/whistleblower, §6.5.5): no sign-in, the tenant is
 * named by its public link, nothing identifying is recorded, calls are throttled per (unrecorded) client. The status
 * check is a POST so the secret never appears in a URL or a log.
 */
@RestController
@RequestMapping("/v1/public/whistleblower")
public class PublicWhistleblowerController {

    private final WhistleblowerService service;
    private final IntakeRateLimiter limiter;

    public PublicWhistleblowerController(WhistleblowerService service, IntakeRateLimiter limiter) {
        this.service = service;
        this.limiter = limiter;
    }

    @PostMapping("/{tenant}")
    @PublicEndpoint("Anonymous whistleblower intake (TRD §6.5.5)")
    public ResponseEntity<ApiResponse<Receipt>> submit(@PathVariable String tenant, @Valid @RequestBody Submission s, HttpServletRequest req) {
        throttle("submit", tenant, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.submit(tenant, s),
                "Report received. Keep your case reference and secret: they are shown only once."));
    }

    @PostMapping("/{tenant}/status")
    @PublicEndpoint("Anonymous whistleblower case status (TRD §6.5.5)")
    public ResponseEntity<ApiResponse<CaseStatus>> status(@PathVariable String tenant, @Valid @RequestBody StatusRequest s, HttpServletRequest req) {
        throttle("status", tenant, req);
        return ResponseEntity.ok(ApiResponse.ok(service.status(tenant, s.caseReference(), s.secret())));
    }

    private void throttle(String kind, String tenant, HttpServletRequest req) {
        if (!limiter.allow(kind, tenant, req.getRemoteAddr())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ESG-429-RATE-LIMIT", "Too many requests. Please try again later.");
        }
    }
}
