package com.lab.edms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;

@Service
public class PdfStampService {

    private static final Logger log = LoggerFactory.getLogger(PdfStampService.class);

    public byte[] applyStamp(byte[] base, StampPayload payload) {
        try (PDDocument doc = Loader.loadPDF(base)) {
            PDPage page = doc.getPage(0);
            PDFont font = loadFont(doc);

            PDRectangle box = page.getMediaBox();
            float x = box.getWidth() - 180 - (payload.stepNumber() % 5) * 6f;
            float y = 60 + (payload.stepNumber() / 5) * 80f;

            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.beginText();
                cs.setFont(font, 9);
                cs.newLineAtOffset(x, y);
                cs.showText("[" + payload.meaning() + "] " + payload.signerDisplayName());
                cs.newLineAtOffset(0, -11);
                cs.showText(payload.signedAt().atZone(java.time.ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")));
                cs.newLineAtOffset(0, -11);
                String fp = payload.pubkeyFingerprint();
                cs.showText("fp:" + (fp == null ? "n/a" : fp.substring(0, Math.min(20, fp.length()))));
                cs.endText();
            }

            String idSeed = payload.versionId() + ":step-" + payload.stepNumber() + ":" + payload.signIntentId();
            PdfDeterministicConfig.lock(doc, idSeed);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.setAllSecurityToBeRemoved(true);
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDFBox stamp failed", e);
        }
    }

    public byte[] applyEffectiveWatermark(byte[] base, String legend) {
        try (PDDocument doc = Loader.loadPDF(base)) {
            PDFont font = loadFont(doc);
            for (PDPage page : doc.getPages()) {
                PDRectangle box = page.getMediaBox();
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    // PDFBox 3.x requires 0.0–1.0 float range (220/255 ≈ 0.863)
                    cs.setNonStrokingColor(220f / 255f, 220f / 255f, 220f / 255f);
                    cs.beginText();
                    cs.setFont(font, 48);
                    cs.setTextMatrix(Matrix.getRotateInstance(
                        Math.toRadians(45), box.getWidth() / 4, box.getHeight() / 3));
                    cs.showText(legend);
                    cs.endText();
                }
            }
            PdfDeterministicConfig.lock(doc, "EFFECTIVE:" + legend);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.setAllSecurityToBeRemoved(true);
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDFBox watermark failed", e);
        }
    }

    private PDFont loadFont(PDDocument doc) {
        // NotoSansKR 폰트 로드 시도, 없으면 Helvetica fallback
        try {
            ClassPathResource res = new ClassPathResource("fonts/NotoSansKR-Regular.ttf");
            if (res.exists()) {
                try (InputStream is = res.getInputStream()) {
                    return PDType0Font.load(doc, is, true);
                }
            }
        } catch (Exception e) {
            log.warn("NotoSansKR font not found, using Helvetica fallback: {}", e.getMessage());
        }
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }
}
