package com.lab.edms.pdf;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for GotenbergClient.
 *
 * The hwp_throws_unsupportedFormat test runs without a real Gotenberg server.
 * The docx conversion test requires a running Gotenberg instance (GOTENBERG_URL).
 *
 * TODO: Add backend/src/test/resources/fixtures/sample.docx for full conversion IT.
 *       A minimal valid docx can be generated with Apache POI:
 *         XWPFDocument doc = new XWPFDocument();
 *         doc.createParagraph().createRun().setText("sample");
 *         doc.write(new FileOutputStream("src/test/resources/fixtures/sample.docx"));
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
class GotenbergClientIT {

    @Autowired
    GotenbergClient client;

    @Test
    void hwp_throws_unsupportedFormat() {
        byte[] hwp = "fake-hwp-bytes".getBytes();
        assertThatThrownBy(() -> client.convertOfficeToPdf("sample.hwp", hwp))
            .isInstanceOf(GotenbergClient.UnsupportedFormatException.class)
            .hasMessageContaining("hwp");
    }
}
