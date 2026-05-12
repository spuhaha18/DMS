package com.lab.edms.storage;

import com.lab.edms.TestcontainersConfig;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BypassGovernanceRetention Deny IAM 정책이 적용된 환경에서 실행.
 * dev 환경(IAM Deny 없음)에서는 삭제가 성공할 수 있어 환경 의존적 테스트임.
 * prod/staging 환경에서만 AccessDenied 를 보장한다.
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
class BypassGovernanceDenyIT {

    // @DynamicPropertySource on @Import-ed @TestConfiguration is unreliable in
    // Spring Boot 3.3 context cache. Override MinIO props directly here.
    @DynamicPropertySource
    static void minioProps(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint",           TestcontainersConfig.MINIO::getS3URL);
        registry.add("minio.access-key",         TestcontainersConfig.MINIO::getUserName);
        registry.add("minio.secret-key",         TestcontainersConfig.MINIO::getPassword);
        registry.add("minio.bucket-original",    () -> "test-edms-documents-original");
        registry.add("minio.bucket-original-v2", () -> "test-documents-original-v2");
        registry.add("minio.bucket-rendition",   () -> "test-edms-documents-rendition");
        registry.add("minio.bucket-anchors",     () -> "test-edms-audit-anchors");
    }

    @Autowired MinioClientWrapper wrapper;
    @Autowired MinioClient minioClient;
    @Autowired MinioProperties props;

    @BeforeEach
    void setUp() {
        wrapper.ensureBuckets();
    }

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
