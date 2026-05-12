package com.lab.edms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PdfStampDeterministicIT {

    @Autowired PdfStampService stamp;

    private byte[] createEmptyPdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private String sha256(byte[] data) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Test
    void sameInput_yieldsSameSha256_5times() throws Exception {
        byte[] basePdf = createEmptyPdf();
        StampPayload payload = new StampPayload(
            1L, 100L, 1, "qa01", "QA Lead",
            Instant.parse("2026-05-12T10:00:00Z"),
            "APPROVED", "BASE64SIG", "fp:abc");

        String first = sha256(stamp.applyStamp(basePdf, payload));
        for (int i = 0; i < 4; i++) {
            assertThat(sha256(stamp.applyStamp(basePdf, payload))).isEqualTo(first);
        }
    }

    @Test
    void watermark_isDeterministic() throws Exception {
        byte[] basePdf = createEmptyPdf();
        String first = sha256(stamp.applyEffectiveWatermark(basePdf, "EFFECTIVE 2026-06-01"));
        for (int i = 0; i < 4; i++) {
            assertThat(sha256(stamp.applyEffectiveWatermark(basePdf, "EFFECTIVE 2026-06-01"))).isEqualTo(first);
        }
    }
}
