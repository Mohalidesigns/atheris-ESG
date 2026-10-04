package com.esg.governance;

import com.esg.governance.GovernanceDtos.BoardMember;
import com.esg.governance.GovernanceDtos.BoardMemberRequest;
import com.esg.governance.GovernanceDtos.CaseDetail;
import com.esg.governance.GovernanceDtos.CaseStatistics;
import com.esg.governance.GovernanceDtos.CaseSummary;
import com.esg.governance.GovernanceDtos.CaseUpdate;
import com.esg.governance.GovernanceDtos.CheckerReport;
import com.esg.governance.GovernanceDtos.Composition;
import com.esg.governance.GovernanceDtos.MyPolicy;
import com.esg.governance.GovernanceDtos.NoteRequest;
import com.esg.governance.GovernanceDtos.Policy;
import com.esg.governance.GovernanceDtos.PolicyRequest;
import com.esg.governance.GovernanceDtos.PolicyTransition;
import com.esg.model.dto.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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

/** M8 Governance & Ethics (WP-34; TRD §6.4.2 Governance): board, governance code checks, policies, whistleblower cases. */
@RestController
@RequestMapping("/v1/governance")
public class GovernanceController {

    private static final Set<String> POLICY_ACTIONS = Set.of("submit", "approve", "send_back", "publish", "review", "retire", "revise");

    private final BoardService board;
    private final PolicyService policies;
    private final WhistleblowerService cases;

    public GovernanceController(BoardService board, PolicyService policies, WhistleblowerService cases) {
        this.board = board;
        this.policies = policies;
        this.cases = cases;
    }

    // ---------------------------------------------------------------- board

    @GetMapping("/board-members")
    @PreAuthorize("hasAuthority('governance:read')")
    public ResponseEntity<ApiResponse<List<BoardMember>>> members(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(defaultValue = "false") boolean includeFormer) {
        return ResponseEntity.ok(ApiResponse.ok(board.list(asOf, includeFormer)));
    }

    @GetMapping("/board-members/{id}")
    @PreAuthorize("hasAuthority('governance:read')")
    public ResponseEntity<ApiResponse<BoardMember>> member(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(board.get(id)));
    }

    @PostMapping("/board-members")
    @PreAuthorize("hasAuthority('governance:create')")
    public ResponseEntity<ApiResponse<BoardMember>> addMember(@Valid @RequestBody BoardMemberRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(board.create(r), "Board member added"));
    }

    @PutMapping("/board-members/{id}")
    @PreAuthorize("hasAuthority('governance:update')")
    public ResponseEntity<ApiResponse<BoardMember>> updateMember(@PathVariable UUID id, @Valid @RequestBody BoardMemberRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(board.update(id, r), "Board member updated"));
    }

    @DeleteMapping("/board-members/{id}")
    @PreAuthorize("hasAuthority('governance:update')")
    public ResponseEntity<Void> deleteMember(@PathVariable UUID id, @RequestParam long version) {
        board.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    /** TRD GET /governance/board-composition. */
    @GetMapping("/board-composition")
    @PreAuthorize("hasAuthority('governance:read')")
    public ResponseEntity<ApiResponse<Composition>> composition(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(ApiResponse.ok(board.composition(asOf)));
    }

    @GetMapping("/code-check")
    @PreAuthorize("hasAuthority('governance:read')")
    public ResponseEntity<ApiResponse<CheckerReport>> codeCheck(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) String ruleSet) {
        if (ruleSet != null && !Set.of("CAMA_2020", "NCCG_2018", "KING_IV").contains(ruleSet)) {
            throw com.esg.exception.FieldValidationException.of("ruleSet", "must be CAMA_2020, NCCG_2018 or KING_IV");
        }
        return ResponseEntity.ok(ApiResponse.ok(board.check(asOf, ruleSet)));
    }

    // ---------------------------------------------------------------- policies

    @GetMapping("/policies")
    @PreAuthorize("hasAuthority('governance:read')")
    public ResponseEntity<ApiResponse<List<Policy>>> policies(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(policies.list(status)));
    }

    @GetMapping("/policies/{id}")
    @PreAuthorize("hasAuthority('governance:read')")
    public ResponseEntity<ApiResponse<Policy>> policy(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(policies.get(id)));
    }

    @PostMapping("/policies")
    @PreAuthorize("hasAuthority('governance:create')")
    public ResponseEntity<ApiResponse<Policy>> createPolicy(@Valid @RequestBody PolicyRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(policies.create(r), "Policy created"));
    }

    @PutMapping("/policies/{id}")
    @PreAuthorize("hasAuthority('governance:update')")
    public ResponseEntity<ApiResponse<Policy>> updatePolicy(@PathVariable UUID id, @Valid @RequestBody PolicyRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(policies.update(id, r), "Policy updated"));
    }

    @DeleteMapping("/policies/{id}")
    @PreAuthorize("hasAuthority('governance:update')")
    public ResponseEntity<Void> deletePolicy(@PathVariable UUID id, @RequestParam long version) {
        policies.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    /** Lifecycle step: submit, approve, send_back, publish, review, retire, revise (each re-checked by the service). */
    @PutMapping("/policies/{id}/{action}")
    @PreAuthorize("hasAnyAuthority('governance:update', 'governance:approve')")
    public ResponseEntity<ApiResponse<Policy>> transition(@PathVariable UUID id, @PathVariable String action,
                                                          @Valid @RequestBody PolicyTransition t) {
        if (!POLICY_ACTIONS.contains(action)) {
            throw com.esg.exception.ApiException.notFound("Policy action");
        }
        return ResponseEntity.ok(ApiResponse.ok(policies.transition(id, action, t.version(), t.reason()), "Policy " + action.replace('_', ' ')));
    }

    /** Everyone signed in: the policies they're asked to acknowledge, and acknowledging one. */
    @GetMapping("/my-policies")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<MyPolicy>>> myPolicies() {
        return ResponseEntity.ok(ApiResponse.ok(policies.mine()));
    }

    @PostMapping("/my-policies/{id}/acknowledge")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MyPolicy>> acknowledge(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(policies.acknowledge(id), "Policy acknowledged"));
    }

    // ---------------------------------------------------------------- whistleblower cases (restricted)

    @GetMapping("/whistleblower/cases")
    @PreAuthorize("hasAuthority('whistleblower:read')")
    public ResponseEntity<ApiResponse<List<CaseSummary>>> cases(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(cases.list(status)));
    }

    @GetMapping("/whistleblower/cases/{id}")
    @PreAuthorize("hasAuthority('whistleblower:read')")
    public ResponseEntity<ApiResponse<CaseDetail>> caseDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(cases.get(id)));
    }

    @PutMapping("/whistleblower/cases/{id}")
    @PreAuthorize("hasAuthority('whistleblower:update')")
    public ResponseEntity<ApiResponse<CaseDetail>> updateCase(@PathVariable UUID id, @Valid @RequestBody CaseUpdate u) {
        return ResponseEntity.ok(ApiResponse.ok(cases.update(id, u), "Case updated"));
    }

    @PostMapping("/whistleblower/cases/{id}/notes")
    @PreAuthorize("hasAuthority('whistleblower:update')")
    public ResponseEntity<ApiResponse<CaseDetail>> addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest n) {
        return ResponseEntity.ok(ApiResponse.ok(cases.addNote(id, n.note()), "Note added"));
    }

    @GetMapping("/whistleblower/investigators")
    @PreAuthorize("hasAuthority('whistleblower:update')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> investigators() {
        return ResponseEntity.ok(ApiResponse.ok(cases.investigatorNames()));
    }

    /** The tenant's public reporting link (anyone can report through it without signing in). */
    @GetMapping("/whistleblower/channel")
    @PreAuthorize("hasAuthority('governance:read')")
    public ResponseEntity<ApiResponse<Map<String, String>>> channel() {
        String slug = cases.tenantSlug();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("slug", slug, "path", "/whistleblower/" + slug)));
    }

    @GetMapping("/whistleblower/statistics")
    @PreAuthorize("hasAnyAuthority('whistleblower:read_summary', 'whistleblower:read')")
    public ResponseEntity<ApiResponse<CaseStatistics>> statistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
        return ResponseEntity.ok(ApiResponse.ok(cases.statistics(since)));
    }
}
