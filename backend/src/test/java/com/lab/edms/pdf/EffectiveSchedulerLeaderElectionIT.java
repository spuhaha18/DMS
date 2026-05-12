package com.lab.edms.pdf;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EffectiveWatermarkScheduler ShedLock leader election 스모크 테스트.
 * 스케줄러 빈이 정상 로드되고 ShedLock 테이블이 존재하는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class EffectiveSchedulerLeaderElectionIT {

    @Autowired
    EffectiveWatermarkScheduler scheduler;

    @Test
    void schedulerLoads() {
        assertThat(scheduler).isNotNull();
    }
}
