package com.esg.reporting;

import com.esg.files.FileDtos.FileInfo;
import com.esg.files.FileService;
import com.esg.reporting.ReportingDtos.ExportJob;
import com.esg.reporting.ReportingDtos.ReportDetail;
import com.esg.reporting.ReportingDtos.Section;
import com.esg.reporting.ReportingDtos.VariableValue;
import com.esg.reporting.ReportingDtos.VersionDetail;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

/**
 * ExportEngine (TRD §8.4; BRD-5.2.4-03; D24 (5)): renders the latest <em>approved</em> version of a report — its sections
 * and frozen data snapshot, never live data — to PDF (Thymeleaf XHTML + Flying Saucer) or Excel (Apache POI), stores
 * the file through the file service and records an export job.
 */
@Service
public class ExportService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK).withZone(ZoneId.of("Africa/Lagos"));
    static final String PDF = "application/pdf";
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final JdbcTemplate jdbc;
    private final ReportService reports;
    private final FileService files;
    private final SpringTemplateEngine xhtml;

    public ExportService(JdbcTemplate jdbc, ReportService reports, FileService files) {
        this.jdbc = jdbc;
        this.reports = reports;
        this.files = files;
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/reports/");
        resolver.setSuffix(".xhtml");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        this.xhtml = new SpringTemplateEngine();
        this.xhtml.setTemplateResolver(resolver);
    }

    /** TRD POST /reports/{id}/generate. */
    @Transactional
    public ExportJob generate(UUID reportId, String format) {
        ReportDetail report = reports.get(reportId);
        VersionDetail approved = reports.latestApproved(reportId);
        UUID job = UUID.randomUUID();
        jdbc.update("insert into reporting.export_jobs (id, report_id, version_label, format, status) values (?, ?, ?, ?, 'running')",
                job, reportId, approved.label(), format);
        byte[] content = "pdf".equals(format) ? pdf(report, approved) : xlsx(report, approved);
        String name = fileName(report.title(), approved.label(), format);
        FileInfo file = files.storeGenerated(content, name, "pdf".equals(format) ? PDF : XLSX, "report_export", reportId);
        jdbc.update("update reporting.export_jobs set status = 'done', file_id = ?, finished_at = now() where id = ?", file.id(), job);
        return reports.get(reportId).exports().stream().filter(e -> e.id().equals(job)).findFirst().orElseThrow();
    }

    /**
     * Phase 3 (3): the report as it stands now (working sections and data snapshot), watermarked DRAFT on every page.
     * Never stored and never an export job: the final export stays gated on approval ({@link #generate}).
     */
    public byte[] draftPreview(UUID reportId) {
        ReportDetail report = reports.get(reportId);
        String meta = tenant() + " · " + period(report) + " · Working version " + report.versionLabel() + " · Not approved — "
                + "preview generated " + DAY.format(java.time.Instant.now());
        return render(report.title(), meta, report.sections(), report.snapshot(), true);
    }

    static String draftFileName(String title, String version) {
        return fileName(title, version + "-DRAFT", "pdf");
    }

    byte[] pdf(ReportDetail report, VersionDetail v) {
        return render(report.title(), tenant() + " · " + period(report) + " · Version " + v.label() + " · Approved " + DAY.format(v.createdAt()),
                v.sections(), v.snapshot(), false);
    }

    private byte[] render(String title, String meta, List<Section> sections, Map<String, VariableValue> snapshot, boolean draft) {
        Context ctx = new Context(Locale.UK);
        ctx.setVariable("title", title);
        ctx.setVariable("meta", meta);
        ctx.setVariable("draft", draft);
        ctx.setVariable("sections", sections.stream().map(s -> Map.of("title", s.title(), "paragraphs", paragraphs(s.rendered()))).toList());
        ctx.setVariable("data", dataRows(snapshot == null ? Map.of() : snapshot));
        String html = xhtml.process("report", ctx);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not render the PDF", e);
        }
    }

    byte[] xlsx(ReportDetail report, VersionDetail v) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            CellStyle wrap = wb.createCellStyle();
            wrap.setWrapText(true);

            Sheet text = wb.createSheet("Report");
            int r = 0;
            r = row(text, r, head, report.title());
            r = row(text, r, null, tenant() + " · " + period(report) + " · Version " + v.label() + " · Approved " + DAY.format(v.createdAt()));
            r++;
            for (Section s : v.sections()) {
                r = row(text, r, head, s.title());
                Row row = text.createRow(r++);
                row.createCell(0).setCellValue(s.rendered() == null ? "" : s.rendered());
                row.getCell(0).setCellStyle(wrap);
                r++;
            }
            text.setColumnWidth(0, 120 * 256);

            Sheet data = wb.createSheet("Data");
            Row h = data.createRow(0);
            String[] cols = {"Variable", "Reported", "Value", "Unit", "Status", "Basis", "Provisional"};
            for (int i = 0; i < cols.length; i++) {
                h.createCell(i).setCellValue(cols[i]);
                h.getCell(i).setCellStyle(head);
            }
            int i = 1;
            for (Map.Entry<String, VariableValue> e : v.snapshot().entrySet()) {
                Row row = data.createRow(i++);
                VariableValue val = e.getValue();
                row.createCell(0).setCellValue(e.getKey());
                row.createCell(1).setCellValue(val.display());
                if (val.value() != null) {
                    row.createCell(2).setCellValue(val.value().doubleValue());
                }
                row.createCell(3).setCellValue(val.unit() == null ? "" : val.unit());
                row.createCell(4).setCellValue(val.status());
                row.createCell(5).setCellValue(val.basis() == null ? "" : val.basis());
                row.createCell(6).setCellValue(val.provisional() == null ? "" : val.provisional());
            }
            for (int c = 0; c < cols.length; c++) {
                data.autoSizeColumn(c);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the workbook", e);
        }
    }

    private static int row(Sheet sheet, int r, CellStyle style, String text) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(text);
        if (style != null) {
            row.getCell(0).setCellStyle(style);
        }
        return r + 1;
    }

    private static List<List<String>> paragraphs(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("\\n\\s*\\n")).map(p -> Arrays.asList(p.split("\\n"))).toList();
    }

    private static List<Map<String, String>> dataRows(Map<String, VariableValue> snapshot) {
        // S7: a provisional value says so next to its basis.
        return snapshot.entrySet().stream().map(e -> Map.of("variable", e.getKey(), "display", e.getValue().display(),
                "basis", (e.getValue().basis() == null ? "" : e.getValue().basis())
                        + (e.getValue().provisional() == null ? "" : " — PROVISIONAL: " + e.getValue().provisional()))).toList();
    }

    private String tenant() {
        return jdbc.queryForObject("select name from auth.tenants where id = auth.current_tenant_id()", String.class);
    }

    private static String period(ReportDetail r) {
        DateTimeFormatter d = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);
        return d.format(r.reportingPeriodStart()) + " to " + d.format(r.reportingPeriodEnd());
    }

    static String fileName(String title, String version, String format) {
        String base = title.replaceAll("[^A-Za-z0-9 _-]", "").trim().replaceAll("\\s+", "-");
        return (base.isEmpty() ? "report" : base) + "-" + version + "." + format;
    }
}
