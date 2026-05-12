package com.lab.edms.pdf;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for PdfRenditionPipeline.
 *
 * pipelineIsLoaded: Spring 컨텍스트가 정상 기동되고 PdfRenditionPipeline 빈이
 *                   주입되는지 확인한다. Gotenberg / MinIO 연결 없이도 동작해야 한다.
 *
 * TODO: 실제 변환 IT — 아래 조건이 충족되면 활성화
 *   1. backend/src/test/resources/fixtures/sample.docx 추가
 *   2. Testcontainers Gotenberg 컨테이너 설정
 *   3. Testcontainers MinIO 컨테이너 설정 (object-lock 지원 버전 필요)
 *
 * TODO: enqueueInitialConversion 흐름 IT
 *   - Document + DocumentVersion INSERT
 *   - enqueueInitialConversion(documentId) 호출
 *   - Document.pdfStatus == "PENDING_CONVERSION" 즉시 확인
 *   - @Async 완료 후 pdfStatus == "CONVERTED" 확인 (CompletableFuture or Awaitility)
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
class PdfRenditionPipelineIT {

    @Autowired
    PdfRenditionPipeline pipeline;

    @Test
    void pipelineIsLoaded() {
        assertThat(pipeline).isNotNull();
    }
}
