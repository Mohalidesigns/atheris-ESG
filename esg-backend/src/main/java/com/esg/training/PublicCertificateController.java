package com.esg.training;

import com.esg.exception.ApiException;
import com.esg.governance.IntakeRateLimiter;
import com.esg.model.dto.ApiResponse;
import com.esg.security.PublicEndpoint;
import com.esg.training.TrainingDtos.Verification;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public certificate verification (WP-42, D37 (5)): anyone with the code (or the QR code) sees whether the certificate
 * is valid, whose it is, for which course and from which organisation — nothing else. Throttled per (unrecorded) client.
 */
@RestController
@RequestMapping("/v1/public/certificates")
public class PublicCertificateController {

    private final CertificateService certificates;
    private final IntakeRateLimiter limiter;

    public PublicCertificateController(CertificateService certificates, IntakeRateLimiter limiter) {
        this.certificates = certificates;
        this.limiter = limiter;
    }

    @GetMapping("/{code}")
    @PublicEndpoint("Certificate verification by code (WP-42)")
    public ResponseEntity<ApiResponse<Verification>> verify(@PathVariable String code, HttpServletRequest req) {
        if (!limiter.allow("certificate", "verify", req.getRemoteAddr())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ESG-429-RATE-LIMIT", "Too many requests. Please try again later.");
        }
        return ResponseEntity.ok(ApiResponse.ok(certificates.verify(code)));
    }
}
