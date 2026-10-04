package com.esg.supplychain;

import com.esg.exception.ApiException;
import com.esg.files.FileDtos;
import com.esg.governance.IntakeRateLimiter;
import com.esg.model.dto.ApiResponse;
import com.esg.security.PublicEndpoint;
import com.esg.supplychain.SupplyDtos.AnswersRequest;
import com.esg.supplychain.SupplyDtos.PublicQuestionnaire;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The supplier's side of a questionnaire (WP-31, D33; TRD §8.6 simplified onboarding): no account, the link's token
 * opens exactly one questionnaire. An unknown, cancelled, reviewed-and-closed or expired link is a plain 404. Calls are
 * throttled per (unrecorded) client.
 */
@RestController
@RequestMapping("/v1/public/supplier-questionnaires")
public class PublicSupplierController {

    private final QuestionnaireService questionnaires;
    private final IntakeRateLimiter limiter;

    public PublicSupplierController(QuestionnaireService questionnaires, IntakeRateLimiter limiter) {
        this.questionnaires = questionnaires;
        this.limiter = limiter;
    }

    @GetMapping("/{token}")
    @PublicEndpoint("Supplier questionnaire by link (WP-31)")
    public ResponseEntity<ApiResponse<PublicQuestionnaire>> view(@PathVariable String token, HttpServletRequest req) {
        throttle(req);
        return ResponseEntity.ok(ApiResponse.ok(questionnaires.withToken(token, questionnaires::publicView)));
    }

    @PutMapping("/{token}/answers")
    @PublicEndpoint("Supplier saves questionnaire answers (WP-31)")
    public ResponseEntity<ApiResponse<PublicQuestionnaire>> save(@PathVariable String token, @Valid @RequestBody AnswersRequest r, HttpServletRequest req) {
        throttle(req);
        return ResponseEntity.ok(ApiResponse.ok(questionnaires.withToken(token, q -> questionnaires.saveAnswers(q, r.answers())), "Answers saved"));
    }

    @PostMapping(value = "/{token}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PublicEndpoint("Supplier uploads questionnaire evidence (WP-31)")
    public ResponseEntity<ApiResponse<FileDtos.FileInfo>> evidence(@PathVariable String token, @RequestPart("file") MultipartFile file,
                                                                   HttpServletRequest req) {
        throttle(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(questionnaires.withToken(token, q -> questionnaires.uploadEvidence(q, file)),
                "Evidence uploaded"));
    }

    @PostMapping("/{token}/submit")
    @PublicEndpoint("Supplier submits the questionnaire (WP-31)")
    public ResponseEntity<ApiResponse<PublicQuestionnaire>> submit(@PathVariable String token, HttpServletRequest req) {
        throttle(req);
        return ResponseEntity.ok(ApiResponse.ok(questionnaires.withToken(token, questionnaires::submit), "Questionnaire submitted. Thank you."));
    }

    private void throttle(HttpServletRequest req) {
        if (!limiter.allow("supplier", "questionnaire", req.getRemoteAddr())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ESG-429-RATE-LIMIT", "Too many requests. Please try again later.");
        }
    }
}
