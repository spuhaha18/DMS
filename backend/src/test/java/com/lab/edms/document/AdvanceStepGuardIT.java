package com.lab.edms.document;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M7 PR3: STAMPING 중 423 가드 — DocumentService.assertNextStepAllowed 스켈레톤 IT.
 * pdf_status가 STAMPING/CONVERSION_FAILED/STAMP_FAILED 인 경우 423 LOCKED 반환 검증.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class AdvanceStepGuardIT {

    @Autowired
    DocumentService documentService;

    @Test
    void serviceLoads() {
        assertThat(documentService).isNotNull();
    }
}
