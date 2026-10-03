package com.esg.supplychain;

import com.esg.model.dto.ApiResponse;
import com.esg.supplychain.SupplyDtos.ActionRequest;
import com.esg.supplychain.SupplyDtos.CorrectiveAction;
import com.esg.supplychain.SupplyDtos.CorrectiveActionRequest;
import com.esg.supplychain.SupplyDtos.ImportResult;
import com.esg.supplychain.SupplyDtos.Overview;
import com.esg.supplychain.SupplyDtos.Person;
import com.esg.supplychain.SupplyDtos.Questionnaire;
import com.esg.supplychain.SupplyDtos.SendRequest;
import com.esg.supplychain.SupplyDtos.Sent;
import com.esg.supplychain.SupplyDtos.Spend;
import com.esg.supplychain.SupplyDtos.SpendRequest;
import com.esg.supplychain.SupplyDtos.Supplier;
import com.esg.supplychain.SupplyDtos.SupplierRequest;
import com.esg.supplychain.SupplyDtos.Template;
import com.esg.supplychain.SupplyDtos.TemplateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** M6 Supply Chain ESG (WP-31; TRD §6.4.2 Supply Chain): suppliers, questionnaires, scorecards, corrective actions, spend. */
@RestController
@RequestMapping("/v1/supply-chain")
public class SupplyChainController {

    private final SupplierService suppliers;
    private final TemplateService templates;
    private final QuestionnaireService questionnaires;
    private final CorrectiveActionService actions;
    private final SpendService spend;
    private final SupplySupport support;

    public SupplyChainController(SupplierService suppliers, TemplateService templates, QuestionnaireService questionnaires,
                                 CorrectiveActionService actions, SpendService spend, SupplySupport support) {
        this.suppliers = suppliers;
        this.templates = templates;
        this.questionnaires = questionnaires;
        this.actions = actions;
        this.spend = spend;
        this.support = support;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('supply_chain:read')")
    public ResponseEntity<ApiResponse<Overview>> overview(@RequestParam(required = false) Integer year) {
        int y = year == null ? support.today().getYear() : year;
        if (y < 2000 || y > support.today().getYear() + 1) {
            throw com.esg.exception.FieldValidationException.of("year", "must be between 2000 and next year");
        }
        return ResponseEntity.ok(ApiResponse.ok(suppliers.overview(y)));
    }

    @GetMapping("/people")
    @PreAuthorize("hasAuthority('supply_chain:read')")
    public ResponseEntity<ApiResponse<List<Person>>> people() {
        return ResponseEntity.ok(ApiResponse.ok(support.holders("update")));
    }

    // ---------------------------------------------------------------- suppliers

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('supply_chain:read')")
    public ResponseEntity<ApiResponse<List<Supplier>>> suppliers(@RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) String riskLevel, @RequestParam(required = false) String tier) {
        return ResponseEntity.ok(ApiResponse.ok(suppliers.list(status, riskLevel, tier)));
    }

    @GetMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('supply_chain:read')")
    public ResponseEntity<ApiResponse<Supplier>> supplier(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(suppliers.get(id)));
    }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAuthority('supply_chain:create')")
    public ResponseEntity<ApiResponse<Supplier>> addSupplier(@Valid @RequestBody SupplierRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(suppliers.create(r), "Supplier added"));
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('supply_chain:update')")
    public ResponseEntity<ApiResponse<Supplier>> updateSupplier(@PathVariable UUID id, @Valid @RequestBody SupplierRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(suppliers.update(id, r), "Supplier updated"));
    }

    @DeleteMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('supply_chain:delete')")
    public ResponseEntity<Void> deleteSupplier(@PathVariable UUID id, @RequestParam long version) {
        suppliers.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- templates

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('supply_chain:read')")
    public ResponseEntity<ApiResponse<List<Template>>> templates() {
        return ResponseEntity.ok(ApiResponse.ok(templates.list()));
    }

    @GetMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('supply_chain:read')")
    public ResponseEntity<ApiResponse<Template>> template(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(templates.get(id)));
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAuthority('supply_chain:create')")
    public ResponseEntity<ApiResponse<Template>> addTemplate(@Valid @RequestBody TemplateRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(templates.create(r), "Template created"));
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('supply_chain:update')")
    public ResponseEntity<ApiResponse<Template>> updateTemplate(@PathVariable UUID id, @Valid @RequestBody TemplateRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(templates.update(id, r), "Template updated"));
    }

    @PostMapping("/templates/{id}/actions")
    @PreAuthorize("hasAuthority('supply_chain:update')")
    public ResponseEntity<ApiResponse<Template>> templateAction(@PathVariable UUID id, @Valid @RequestBody ActionRequest a) {
        return ResponseEntity.ok(ApiResponse.ok(templates.act(id, a.action(), a.version())));
    }

    // ---------------------------------------------------------------- questionnaires

    @GetMapping("/questionnaires")
    @PreAuthorize("hasAuthority('supply_chain:read')")
    public ResponseEntity<ApiResponse<List<Questionnaire>>> questionnaires(@RequestParam(required = false) UUID supplierId,
                                                                           @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(questionnaires.list(supplierId, status)));
    }

    @GetMapping("/questionnaires/{id}")
    @PreAuthorize("hasAuthority('supply_chain:read')")
    public ResponseEntity<ApiResponse<Questionnaire>> questionnaire(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(questionnaires.get(id)));
    }

    @PostMapping("/questionnaires")
    @PreAuthorize("hasAuthority('supply_chain:update')")
    public ResponseEntity<ApiResponse<Sent>> send(@Valid @RequestBody SendRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(questionnaires.send(r),
                "Questionnaire sent. Copy the link now: it is shown only once."));
    }

    @PostMapping("/questionnaires/{id}/resend")
    @PreAuthorize("hasAuthority('supply_chain:update')")
    public ResponseEntity<ApiResponse<Sent>> resend(@PathVariable UUID id, @RequestParam long version) {
        return ResponseEntity.ok(ApiResponse.ok(questionnaires.resend(id, version), "New link created; the previous one no longer works."));
    }

    @PostMapping("/questionnaires/{id}/actions")
    @PreAuthorize("hasAuthority('supply_chain:update') or hasAuthority('supply_chain:approve')")
    public ResponseEntity<ApiResponse<Questionnaire>> questionnaireAction(@PathVariable UUID id, @Valid @RequestBody ActionRequest a) {
        return ResponseEntity.ok(ApiResponse.ok(questionnaires.act(id, a)));
    }

    // ---------------------------------------------------------------- corrective actions

    @GetMapping("/actions")
    @PreAuthorize("hasAuthority('supply_chain:read')")
    public ResponseEntity<ApiResponse<List<CorrectiveAction>>> correctiveActions(@RequestParam(required = false) UUID supplierId,
                                                                                 @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(actions.list(supplierId, status)));
    }

    @GetMapping("/actions/{id}")
    @PreAuthorize("hasAuthority('supply_chain:read')")
    public ResponseEntity<ApiResponse<CorrectiveAction>> correctiveAction(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(actions.get(id)));
    }

    @PostMapping("/actions")
    @PreAuthorize("hasAuthority('supply_chain:create')")
    public ResponseEntity<ApiResponse<CorrectiveAction>> raise(@Valid @RequestBody CorrectiveActionRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(actions.create(r), "Corrective action raised"));
    }

    @PutMapping("/actions/{id}")
    @PreAuthorize("hasAuthority('supply_chain:update')")
    public ResponseEntity<ApiResponse<CorrectiveAction>> updateAction(@PathVariable UUID id, @Valid @RequestBody CorrectiveActionRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(actions.update(id, r), "Corrective action updated"));
    }

    @PostMapping("/actions/{id}/steps")
    @PreAuthorize("hasAuthority('supply_chain:update') or hasAuthority('supply_chain:approve')")
    public ResponseEntity<ApiResponse<CorrectiveAction>> step(@PathVariable UUID id, @Valid @RequestBody ActionRequest a) {
        return ResponseEntity.ok(ApiResponse.ok(actions.act(id, a)));
    }

    // ---------------------------------------------------------------- spend

    @GetMapping("/spend")
    @PreAuthorize("hasAuthority('supply_chain:read')")
    public ResponseEntity<ApiResponse<List<Spend>>> spendLines(@RequestParam(required = false) UUID supplierId,
                                                              @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(spend.list(supplierId, year)));
    }

    @GetMapping("/spend/categories")
    @PreAuthorize("hasAuthority('supply_chain:read')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> spendCategories() {
        return ResponseEntity.ok(ApiResponse.ok(spend.categories()));
    }

    @GetMapping("/spend/{id}")
    @PreAuthorize("hasAuthority('supply_chain:read')")
    public ResponseEntity<ApiResponse<Spend>> spendLine(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(spend.get(id)));
    }

    @PostMapping("/spend")
    @PreAuthorize("hasAuthority('supply_chain:create') and hasAuthority('carbon_data:create')")
    public ResponseEntity<ApiResponse<Spend>> addSpend(@Valid @RequestBody SpendRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(spend.create(r), "Spend recorded and Scope 3 calculated"));
    }

    @PutMapping("/spend/{id}")
    @PreAuthorize("hasAuthority('supply_chain:update') and hasAuthority('carbon_data:update')")
    public ResponseEntity<ApiResponse<Spend>> updateSpend(@PathVariable UUID id, @Valid @RequestBody SpendRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(spend.update(id, r), "Spend updated and Scope 3 recalculated"));
    }

    @DeleteMapping("/spend/{id}")
    @PreAuthorize("hasAuthority('supply_chain:update') and hasAuthority('carbon_data:update')")
    public ResponseEntity<Void> deleteSpend(@PathVariable UUID id, @RequestParam long version) {
        spend.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/spend/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('supply_chain:create') and hasAuthority('carbon_data:create')")
    public ResponseEntity<ApiResponse<ImportResult>> importSpend(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(spend.importFile(file)));
    }
}
