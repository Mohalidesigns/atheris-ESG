package com.esg.investor;

import com.esg.files.ContentDisposition;
import com.esg.investor.InvestorDtos.AccessEntry;
import com.esg.investor.InvestorDtos.ActionRequest;
import com.esg.investor.InvestorDtos.AllocationRequest;
import com.esg.investor.InvestorDtos.Bond;
import com.esg.investor.InvestorDtos.BondRequest;
import com.esg.investor.InvestorDtos.Dashboard;
import com.esg.investor.InvestorDtos.DfiRequest;
import com.esg.investor.InvestorDtos.DfiTemplate;
import com.esg.investor.InvestorDtos.DocumentRequest;
import com.esg.investor.InvestorDtos.GenerateRequest;
import com.esg.investor.InvestorDtos.Impact;
import com.esg.investor.InvestorDtos.ImpactRequest;
import com.esg.investor.InvestorDtos.Person;
import com.esg.investor.InvestorDtos.Preview;
import com.esg.investor.InvestorDtos.Room;
import com.esg.investor.InvestorDtos.RoomRequest;
import com.esg.investor.InvestorDtos.Scorecard;
import com.esg.investor.InvestorDtos.Settings;
import com.esg.investor.InvestorDtos.SettingsRequest;
import com.esg.investor.InvestorDtos.ShareRequest;
import com.esg.investor.InvestorDtos.SharedReport;
import com.esg.model.dto.ApiResponse;
import com.esg.reporting.ReportingDtos.ReportDetail;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

/**
 * M10 Investor Portal (WP-40, D35; TRD §6.4.2 Investor): scorecards, shared reports, DFI reports, data rooms,
 * sustainable finance instruments and IRIS+ impact metrics. investor:read sees what is published or shared;
 * investor:share prepares it; investor:configure sets the scorecard weights.
 */
@RestController
@RequestMapping("/v1/investor")
public class InvestorController {

    private final ScorecardService scorecards;
    private final SharedReportService reports;
    private final DataRoomService rooms;
    private final BondService bonds;
    private final ImpactService impact;
    private final InvestorSupport support;
    private final JdbcTemplate jdbc;

    public InvestorController(ScorecardService scorecards, SharedReportService reports, DataRoomService rooms, BondService bonds, ImpactService impact,
                              InvestorSupport support, JdbcTemplate jdbc) {
        this.scorecards = scorecards;
        this.reports = reports;
        this.rooms = rooms;
        this.bonds = bonds;
        this.impact = impact;
        this.support = support;
        this.jdbc = jdbc;
    }

    /** TRD GET /investor/dashboards: everything the caller may see in one call (the INVESTOR_VIEWER's home page). */
    @GetMapping("/dashboards")
    @PreAuthorize("hasAuthority('investor:read')")
    public ResponseEntity<ApiResponse<Dashboard>> dashboard() {
        List<Scorecard> all = scorecards.list();
        List<Scorecard> published = all.stream().filter(s -> "published".equals(s.status())).toList();
        String company = jdbc.queryForObject("select name from auth.tenants where id = auth.current_tenant_id()", String.class);
        return ResponseEntity.ok(ApiResponse.ok(new Dashboard(company, support.manager(), published.isEmpty() ? null : published.get(0), published,
                reports.list().stream().filter(r -> r.revokedAt() == null).toList(), rooms.list(), bonds.list(), impact.list(null))));
    }

    // ---------------------------------------------------------------- scorecards

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<Settings>> settings() {
        return ResponseEntity.ok(ApiResponse.ok(scorecards.settings()));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('investor:configure')")
    public ResponseEntity<ApiResponse<Settings>> updateSettings(@Valid @RequestBody SettingsRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(scorecards.updateSettings(r)));
    }

    @GetMapping("/scorecards")
    @PreAuthorize("hasAuthority('investor:read')")
    public ResponseEntity<ApiResponse<List<Scorecard>>> scorecards() {
        return ResponseEntity.ok(ApiResponse.ok(scorecards.list()));
    }

    @GetMapping("/scorecards/preview")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<Preview>> preview(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(scorecards.preview(support.year(year))));
    }

    @GetMapping("/scorecards/{id}")
    @PreAuthorize("hasAuthority('investor:read')")
    public ResponseEntity<ApiResponse<Scorecard>> scorecard(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(scorecards.get(id)));
    }

    @PostMapping("/scorecards")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<Scorecard>> generate(@Valid @RequestBody GenerateRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(scorecards.generate(r)));
    }

    @PostMapping("/scorecards/{id}/actions")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<Scorecard>> scorecardAction(@PathVariable UUID id, @Valid @RequestBody ActionRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(scorecards.act(id, r)));
    }

    // ---------------------------------------------------------------- shared reports and DFI reports

    @GetMapping("/shared-reports")
    @PreAuthorize("hasAuthority('investor:read')")
    public ResponseEntity<ApiResponse<List<SharedReport>>> sharedReports() {
        return ResponseEntity.ok(ApiResponse.ok(reports.list()));
    }

    @PostMapping("/shared-reports")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<SharedReport>> share(@Valid @RequestBody ShareRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(reports.share(r)));
    }

    @DeleteMapping("/shared-reports/{id}")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        reports.revoke(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shared-reports/{id}/download")
    @PreAuthorize("hasAuthority('investor:read')")
    public ResponseEntity<byte[]> downloadReport(@PathVariable UUID id) throws IOException {
        return file(reports.download(id));
    }

    @GetMapping("/dfi-templates")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<List<DfiTemplate>>> dfiTemplates() {
        return ResponseEntity.ok(ApiResponse.ok(reports.dfiTemplates()));
    }

    /** TRD POST /investor/dfi-reports: a Reporting report from a DFI template (approval and export as usual). */
    @PostMapping("/dfi-reports")
    @PreAuthorize("hasAuthority('investor:share') and hasAuthority('reports:create')")
    public ResponseEntity<ApiResponse<ReportDetail>> dfiReport(@Valid @RequestBody DfiRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(reports.createDfiReport(r)));
    }

    // ---------------------------------------------------------------- data rooms

    @GetMapping("/people")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<List<Person>>> people() {
        return ResponseEntity.ok(ApiResponse.ok(support.investors()));
    }

    @GetMapping("/data-rooms")
    @PreAuthorize("hasAuthority('investor:read')")
    public ResponseEntity<ApiResponse<List<Room>>> rooms() {
        return ResponseEntity.ok(ApiResponse.ok(rooms.list()));
    }

    @GetMapping("/data-rooms/{id}")
    @PreAuthorize("hasAuthority('investor:read')")
    public ResponseEntity<ApiResponse<Room>> room(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(rooms.open(id)));
    }

    @PostMapping("/data-rooms")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<Room>> createRoom(@Valid @RequestBody RoomRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(rooms.create(r)));
    }

    @PutMapping("/data-rooms/{id}")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<Room>> updateRoom(@PathVariable UUID id, @Valid @RequestBody RoomRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(rooms.update(id, r)));
    }

    @DeleteMapping("/data-rooms/{id}")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<Void> deleteRoom(@PathVariable UUID id) {
        rooms.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/data-rooms/{id}/documents")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<Room>> addDocument(@PathVariable UUID id, @Valid @RequestBody DocumentRequest r) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(rooms.addDocument(id, r)));
    }

    @DeleteMapping("/data-rooms/{id}/documents/{documentId}")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<Room>> removeDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.ok(rooms.removeDocument(id, documentId)));
    }

    @GetMapping("/data-rooms/{id}/documents/{documentId}/download")
    @PreAuthorize("hasAuthority('investor:read')")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable UUID id, @PathVariable UUID documentId) throws IOException {
        return file(rooms.download(id, documentId));
    }

    @GetMapping("/data-rooms/{id}/access-log")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<List<AccessEntry>>> accessLog(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(rooms.accessLog(id)));
    }

    // ---------------------------------------------------------------- sustainable finance instruments

    @GetMapping("/bonds")
    @PreAuthorize("hasAuthority('investor:read')")
    public ResponseEntity<ApiResponse<List<Bond>>> bonds() {
        return ResponseEntity.ok(ApiResponse.ok(bonds.list()));
    }

    @GetMapping("/bonds/{id}")
    @PreAuthorize("hasAuthority('investor:read')")
    public ResponseEntity<ApiResponse<Bond>> bond(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(bonds.get(id)));
    }

    @PostMapping("/bonds")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<Bond>> createBond(@Valid @RequestBody BondRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(bonds.create(r)));
    }

    @PutMapping("/bonds/{id}")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<Bond>> updateBond(@PathVariable UUID id, @Valid @RequestBody BondRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(bonds.update(id, r)));
    }

    @DeleteMapping("/bonds/{id}")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<Void> deleteBond(@PathVariable UUID id) {
        bonds.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bonds/{id}/allocations")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<Bond>> addAllocation(@PathVariable UUID id, @Valid @RequestBody AllocationRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(bonds.addAllocation(id, r)));
    }

    @PutMapping("/bonds/{id}/allocations/{allocationId}")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<Bond>> updateAllocation(@PathVariable UUID id, @PathVariable UUID allocationId, @Valid @RequestBody AllocationRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(bonds.updateAllocation(id, allocationId, r)));
    }

    @DeleteMapping("/bonds/{id}/allocations/{allocationId}")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<Bond>> deleteAllocation(@PathVariable UUID id, @PathVariable UUID allocationId) {
        return ResponseEntity.ok(ApiResponse.ok(bonds.deleteAllocation(id, allocationId)));
    }

    // ---------------------------------------------------------------- impact metrics

    @GetMapping("/impact-metrics")
    @PreAuthorize("hasAuthority('investor:read')")
    public ResponseEntity<ApiResponse<List<Impact>>> impact(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(impact.list(year)));
    }

    @GetMapping("/impact-metrics/{id}")
    @PreAuthorize("hasAuthority('investor:read')")
    public ResponseEntity<ApiResponse<Impact>> impactMetric(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(impact.get(id)));
    }

    @PostMapping("/impact-metrics")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<Impact>> createImpact(@Valid @RequestBody ImpactRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(impact.create(r)));
    }

    @PutMapping("/impact-metrics/{id}")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<ApiResponse<Impact>> updateImpact(@PathVariable UUID id, @Valid @RequestBody ImpactRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(impact.update(id, r)));
    }

    @DeleteMapping("/impact-metrics/{id}")
    @PreAuthorize("hasAuthority('investor:share')")
    public ResponseEntity<Void> deleteImpact(@PathVariable UUID id) {
        impact.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<byte[]> file(SharedReportService.Download d) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment(d.fileName()))
                .contentType(MediaType.parseMediaType(d.contentType() == null ? "application/octet-stream" : d.contentType())).body(d.content());
    }
}
