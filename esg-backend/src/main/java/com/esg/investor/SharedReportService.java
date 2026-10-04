package com.esg.investor;

import com.esg.exception.ApiException;
import com.esg.files.FileService;
import com.esg.investor.InvestorDtos.DfiRequest;
import com.esg.investor.InvestorDtos.DfiTemplate;
import com.esg.investor.InvestorDtos.ShareRequest;
import com.esg.investor.InvestorDtos.SharedReport;
import com.esg.reporting.ExportService;
import com.esg.reporting.ReportService;
import com.esg.reporting.ReportingDtos.ReportDetail;
import com.esg.reporting.ReportingDtos.ReportRequest;
import com.esg.reporting.ReportingDtos.VersionDetail;
import com.esg.security.CurrentUser;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports shared with investors (D35 (3)) and DFI reports (D35 (5)). Sharing needs an approved version; its PDF export
 * is what investors download (generated at sharing if there isn't one). A DFI report is an ordinary Reporting report
 * created from a DFI template, so it goes through the same approval and export.
 */
@Service
public class SharedReportService {

    private static final String SELECT = "select s.*, r.title, r.reporting_period_start, r.reporting_period_end, r.framework_code, "
            + "u.first_name || ' ' || u.last_name as shared_by_name, f.original_name from investor.shared_reports s "
            + "join reporting.reports r on r.id = s.report_id left join auth.users u on u.id = s.shared_by "
            + "left join files.file_metadata f on f.id = s.export_file_id ";

    private final JdbcTemplate jdbc;
    private final InvestorSupport support;
    private final ReportService reports;
    private final ExportService exports;
    private final FileService files;

    public SharedReportService(JdbcTemplate jdbc, InvestorSupport support, ReportService reports, ExportService exports, FileService files) {
        this.jdbc = jdbc;
        this.support = support;
        this.reports = reports;
        this.exports = exports;
        this.files = files;
    }

    public List<SharedReport> list() {
        return jdbc.query(SELECT + "where r.deleted_at is null" + (support.manager() ? "" : " and s.revoked_at is null")
                + " order by s.revoked_at is not null, s.created_at desc", (rs, i) -> shared(rs));
    }

    @Transactional
    public SharedReport share(ShareRequest r) {
        ReportDetail report = reports.get(r.reportId());
        VersionDetail approved = reports.latestApproved(r.reportId());
        UUID file = report.exports().stream().filter(e -> "done".equals(e.status()) && "pdf".equals(e.format()) && approved.label().equals(e.versionLabel()))
                .map(com.esg.reporting.ReportingDtos.ExportJob::fileId).findFirst()
                .orElseGet(() -> exports.generate(r.reportId(), "pdf").fileId());
        jdbc.update("update investor.shared_reports set revoked_at = now(), revoked_by = ?, version = version + 1 where report_id = ? and revoked_at is null",
                CurrentUser.access().userId(), r.reportId());
        UUID id = UUID.randomUUID();
        jdbc.update("insert into investor.shared_reports (id, report_id, version_label, export_file_id, note, shared_by) values (?, ?, ?, ?, ?, ?)", id,
                r.reportId(), approved.label(), file, ScorecardService.blank(r.note()), CurrentUser.access().userId());
        return get(id);
    }

    @Transactional
    public void revoke(UUID id) {
        SharedReport s = get(id);
        if (s.revokedAt() != null) {
            throw ApiException.conflict("ESG-409-STATE", "This report is no longer shared.");
        }
        jdbc.update("update investor.shared_reports set revoked_at = now(), revoked_by = ?, version = version + 1 where id = ?",
                CurrentUser.access().userId(), id);
    }

    SharedReport get(UUID id) {
        SharedReport s = jdbc.query(SELECT + "where s.id = ? and r.deleted_at is null", (rs, i) -> shared(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Shared report"));
        if (!support.manager() && s.revokedAt() != null) {
            throw ApiException.notFound("Shared report");
        }
        return s;
    }

    /** The shared PDF, stamped with who downloaded it and when. */
    public Download download(UUID id) throws IOException {
        SharedReport s = get(id);
        UUID fileId = jdbc.queryForObject("select export_file_id from investor.shared_reports where id = ?", UUID.class, id);
        FileService.Download d = files.open(fileId, null);
        byte[] content;
        try (InputStream in = d.stream()) {
            content = Watermarker.stampOrRefuse(in.readAllBytes(), stamp());
        }
        return new Download(content, s.fileName(), "application/pdf");
    }

    public record Download(byte[] content, String fileName, String contentType) {}

    String stamp() {
        var u = jdbc.queryForMap("select first_name || ' ' || last_name as name, email from auth.users where id = ?", CurrentUser.access().userId());
        return "Shared with " + u.get("name") + " <" + u.get("email") + "> on " + support.today() + " - confidential";
    }

    // ---------------------------------------------------------------- DFI reports

    public List<DfiTemplate> dfiTemplates() {
        return jdbc.query("select template_key, id, name, description, source, content_status from reporting.report_templates "
                + "where tenant_id is null and template_key like 'dfi\\_%' and is_active order by name",
                (rs, i) -> new DfiTemplate(rs.getString(1), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)));
    }

    @Transactional
    public ReportDetail createDfiReport(DfiRequest r) {
        int year = support.year(r.year());
        DfiTemplate t = dfiTemplates().stream().filter(x -> x.key().equals(r.template())).findFirst()
                .orElseThrow(() -> com.esg.exception.FieldValidationException.of("template", "must be an available DFI template"));
        String title = r.title() == null || r.title().isBlank() ? t.name() + " " + year : r.title().trim();
        return reports.create(new ReportRequest(t.templateId(), title, null, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), null));
    }

    private SharedReport shared(ResultSet rs) throws SQLException {
        return new SharedReport(rs.getObject("id", UUID.class), rs.getObject("report_id", UUID.class), rs.getString("title"), rs.getString("version_label"),
                rs.getObject("reporting_period_start", LocalDate.class), rs.getObject("reporting_period_end", LocalDate.class), rs.getString("framework_code"),
                rs.getString("note"), rs.getString("shared_by_name"), rs.getTimestamp("created_at").toInstant(),
                ScorecardService.instant(rs.getTimestamp("revoked_at")), rs.getString("original_name"));
    }
}
