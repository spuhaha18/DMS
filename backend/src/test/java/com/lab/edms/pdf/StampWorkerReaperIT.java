package com.lab.edms.pdf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M7 PR2: StampWorkerReaper 컨텍스트 로딩 확인 스켈레톤 IT.
 */
@SpringBootTest
@ActiveProfiles("test")
class StampWorkerReaperIT {

    @Autowired
    StampWorkerReaper reaper;

    @Test
    void reaperLoaded() {
        assertThat(reaper).isNotNull();
    }
}
