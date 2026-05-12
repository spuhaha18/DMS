package com.lab.edms.pdf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M7 PR2: PdfRenditionPipeline.applyStampForStep() 누적 스켈레톤 IT.
 *
 * 실제 5단계 누적 검증은 Gotenberg + DB + MinIO 모두 필요하므로
 * 현재는 컨텍스트 로딩 확인만 수행한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class PdfStampAccumulationIT {

    @Autowired
    PdfRenditionPipeline pipeline;

    @Test
    void pipelineLoaded() {
        assertThat(pipeline).isNotNull();
    }
}
