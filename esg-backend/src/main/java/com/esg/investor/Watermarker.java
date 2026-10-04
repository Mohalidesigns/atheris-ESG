package com.esg.investor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

/** Stamps who downloaded a PDF, and when, across every page (data rooms, D35 (4)). */
final class Watermarker {
    private Watermarker() {}

    /** Stamps the PDF, or refuses (422 ESG-422-WATERMARK) when it can't be read: never hands it out unmarked. */
    static byte[] stampOrRefuse(byte[] pdf, String text) {
        try {
            return stamp(pdf, text);
        } catch (IOException e) {
            throw new com.esg.exception.ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "ESG-422-WATERMARK",
                    "This PDF can't be watermarked (it may be damaged or password-protected). Replace it with a readable copy.");
        }
    }

    /** Whether the PDF can be watermarked (checked when it is added to a watermarked room). */
    static boolean stampable(byte[] pdf) {
        try {
            stamp(pdf, "check");
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    static byte[] stamp(byte[] pdf, String text) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            String line = ascii(text);
            for (PDPage page : doc.getPages()) {
                float w = page.getMediaBox().getWidth();
                float h = page.getMediaBox().getHeight();
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                    gs.setNonStrokingAlphaConstant(0.18f);
                    cs.setGraphicsStateParameters(gs);
                    cs.setNonStrokingColor(0.6f, 0f, 0f);
                    float size = 22;
                    float width = font.getStringWidth(line) / 1000 * size;
                    cs.beginText();
                    cs.setFont(font, size);
                    cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(35), (w - width * 0.82f) / 2, h * 0.25f));
                    cs.showText(line);
                    cs.endText();
                    cs.beginText();
                    cs.setFont(font, 8);
                    cs.setTextMatrix(Matrix.getTranslateInstance(24, 14));
                    cs.showText(line);
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Standard-14 fonts only cover Latin-1; anything else becomes '?'. */
    private static String ascii(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            b.append(c >= 32 && c < 256 ? c : '?');
        }
        return b.toString();
    }
}
