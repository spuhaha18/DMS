package com.lab.edms.signature;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M7 PR3: SignIntent → Manifest 전환 흐름 스켈레톤 IT.
 * sign() 트랜잭션이 sign_intents만 INSERT하고, afterCommit enqueue 후
 * 워커가 manifest INSERT + status=STAMPED 전이하는 흐름을 검증.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class SignIntentToManifestIT {

    @Autowired
    SignIntentRepository signIntentRepo;

    @Test
    void repositoryLoads() {
        assertThat(signIntentRepo).isNotNull();
    }
}
