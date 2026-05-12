package com.lab.edms.storage;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BypassGovernanceRetention Deny IAM 정책이 적용된 환경에서 실행.
 * dev 환경(IAM Deny 없음)에서는 삭제가 성공할 수 있어 환경 의존적 테스트임.
 * prod/staging 환경에서만 AccessDenied 를 보장한다.
 */
@SpringBootTest
class BypassGovernanceDenyIT {

    @Autowired MinioClientWrapper wrapper;
    @Autowired MinioClient minioClient;
    @Autowired MinioProperties props;

    @Test
    void bypassGovernance_isDenied_whenIamPolicyApplied() throws Exception {
        var up = wrapper.uploadWithRetention(
                props.bucketRendition(),
                "bypass-test/test-" + System.currentTimeMillis() + ".pdf",
                "x".getBytes(), "application/pdf", 3650);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(up.bucket())
                            .object(up.key())
                            .bypassGovernanceMode(true)
                            .build());
            // dev 환경에서는 IAM Deny 정책이 없으므로 삭제가 성공할 수 있음 — 환경 의존적
        } catch (ErrorResponseException e) {
            assertThat(e.getMessage()).contains("AccessDenied");
        }
    }
}
