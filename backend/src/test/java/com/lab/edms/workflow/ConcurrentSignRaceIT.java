package com.lab.edms.workflow;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M7 PR3: 동시 결재 race 가드 스켈레톤 IT.
 * PdfRenditionPipeline.applyStampForStep 시 DocumentVersion FOR UPDATE 락으로
 * 동시 stamp 경쟁 조건 방지 검증.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class ConcurrentSignRaceIT {

    @Autowired
    WorkflowService workflowService;

    @Test
    void serviceLoads() {
        assertThat(workflowService).isNotNull();
    }
}
