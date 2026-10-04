package com.esg.training;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.security.CurrentUser;
import com.esg.training.TrainingDtos.Certificate;
import com.esg.training.TrainingDtos.Verification;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Certificates (WP-42, D37 (5); TRD §8.12 CertificateGenerator): a PDF with the verification code and a QR code to the
 * public verification page; public verification by code (no session); revocation with a reason.
 */
@Service
public class CertificateService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);

    private final JdbcTemplate jdbc;
    private final EnrollmentService enrollments;

    public CertificateService(JdbcTemplate jdbc, EnrollmentService enrollments) {
        this.jdbc = jdbc;
        this.enrollments = enrollments;
    }

    public Certificate get(UUID id) {
        List<Map<String, Object>> owner = jdbc.queryForList("select user_id from training.certificates where id = ?", id);
        if (owner.isEmpty() || (!owner.get(0).get("user_id").equals(CurrentUser.userIdOrNull()) && !CurrentUser.hasPermission("training:update"))) {
            throw ApiException.notFound("Certificate");
        }
        return jdbc.query("select * from training.certificates where id = ?", (r, i) -> enrollments.certificate(r), id).get(0);
    }

    public List<Certificate> list() {
        return jdbc.query("select * from training.certificates order by issued_on desc, holder_name", (r, i) -> enrollments.certificate(r));
    }

    @Transactional
    public Certificate revoke(UUID id, String reason) {
        get(id);
        if (reason == null || reason.isBlank()) {
            throw FieldValidationException.of("note", "say why the certificate is revoked");
        }
        if (jdbc.update("update training.certificates set revoked_at = now(), revoked_by = ?, revoke_reason = ?, version = version + 1 where id = ? "
                + "and revoked_at is null", CurrentUser.access().userId(), reason.trim(), id) == 0) {
            throw ApiException.conflict("ESG-409-STATE", "This certificate is already revoked.");
        }
        return get(id);
    }

    /** Public: what anyone holding the code may know (D37 (5)). */
    public Verification verify(String code) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!c.matches("^[A-HJ-NP-Z2-9]{10}$")) {
            throw ApiException.notFound("Certificate");
        }
        List<Verification> rows = jdbc.query("select * from training.certificate_by_code(?)", (rs, i) -> new Verification(c,
                    TrainingMath.status(rs.getObject("expires_on", LocalDate.class), rs.getBoolean("revoked"), enrollments.today()), rs.getString("holder_name"),
                    rs.getString("course_title"), rs.getString("organisation"), rs.getObject("issued_on", LocalDate.class),
                    rs.getObject("expires_on", LocalDate.class)), c);
        return rows.stream().findFirst().orElseThrow(() -> ApiException.notFound("Certificate"));
    }

    /** A one-page landscape PDF: holder, course, organisation, dates, code and a QR code to the verification page. */
    public byte[] pdf(UUID id) throws IOException {
        Certificate c = get(id);
        String organisation = jdbc.queryForObject("select name from auth.tenants where id = auth.current_tenant_id()", String.class);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDRectangle a4 = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            PDPage page = new PDPage(a4);
            doc.addPage(page);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float w = a4.getWidth();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setLineWidth(3);
                cs.addRect(28, 28, w - 56, a4.getHeight() - 56);
                cs.stroke();
                centred(cs, bold, 30, w, 470, "Certificate of completion");
                centred(cs, regular, 14, w, 430, "This certifies that");
                centred(cs, bold, 24, w, 395, latin1(c.holderName()));
                centred(cs, regular, 14, w, 360, "has successfully completed");
                centred(cs, bold, 18, w, 330, latin1(c.courseTitle()));
                String scoreLine = c.score() == null ? "" : " with a score of " + c.score() + "%";
                centred(cs, regular, 12, w, 295, latin1("Issued by " + organisation + " on " + DAY.format(c.issuedOn()) + scoreLine));
                if (c.expiresOn() != null) {
                    centred(cs, regular, 12, w, 277, "Valid until " + DAY.format(c.expiresOn()));
                }
                text(cs, regular, 10, 60, 90, "Verification code: " + c.code());
                text(cs, regular, 9, 60, 74, latin1("Check it at " + c.verifyUrl()));
                drawQr(cs, c.verifyUrl(), w - 60 - 120, 60, 120);
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    static BitMatrix qr(String content) {
        try {
            return new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 0));
        } catch (WriterException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void drawQr(PDPageContentStream cs, String content, float x, float y, float size) throws IOException {
        BitMatrix m = qr(content);
        float cell = size / m.getWidth();
        cs.setNonStrokingColor(0f, 0f, 0f);
        for (int row = 0; row < m.getHeight(); row++) {
            for (int col = 0; col < m.getWidth(); col++) {
                if (m.get(col, row)) {
                    cs.addRect(x + col * cell, y + size - (row + 1) * cell, cell, cell);
                }
            }
        }
        cs.fill();
    }

    private static void centred(PDPageContentStream cs, PDType1Font f, float size, float width, float y, String s) throws IOException {
        float tw = f.getStringWidth(s) / 1000 * size;
        text(cs, f, size, (width - tw) / 2, y, s);
    }

    private static void text(PDPageContentStream cs, PDType1Font f, float size, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(f, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    private static String latin1(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            b.append(c >= 32 && c < 256 ? c : '?');
        }
        return b.toString();
    }
}
