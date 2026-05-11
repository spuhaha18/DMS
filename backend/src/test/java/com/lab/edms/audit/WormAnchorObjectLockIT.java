package com.lab.edms.audit;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.storage.MinioClientWrapper;
import com.lab.edms.storage.MinioProperties;
import io.minio.GetObjectRetentionArgs;
import io.minio.MinioClient;
import io.minio.messages.Retention;
import io.minio.messages.RetentionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
class WormAnchorObjectLockIT {

    // @DynamicPropertySource on @Import-ed @TestConfiguration is unreliable in
    // Spring Boot 3.3 context cache. Override MinIO props directly here.
    @DynamicPropertySource
    static void minioProps(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint",       TestcontainersConfig.MINIO::getS3URL);
        registry.add("minio.access-key",     TestcontainersConfig.MINIO::getUserName);
        registry.add("minio.secret-key",     TestcontainersConfig.MINIO::getPassword);
        registry.add("minio.bucket-anchors", () -> "test-edms-audit-anchors");
    }

    @Autowired MinioClientWrapper wrapper;
    @Autowired MinioClient minio;
    @Autowired MinioProperties props;

    @BeforeEach
    void setUp() {
        // ensures the anchor bucket exists (with Object Lock) before each test
        wrapper.ensureBuckets();
    }

    /**
     * OQ-AUD-007: uploadWithRetention 이 COMPLIANCE retention 메타데이터를 올바르게 설정하는지 검증.
     * Testcontainers MinIO 단일 노드는 COMPLIANCE 삭제 거부를 실제로 강제하지 않으므로,
     * 대신 getObjectRetention 으로 retention 메타데이터(mode + retainUntilDate)를 확인한다.
     * 실제 삭제 거부(OQ-AUD-007 수동) 는 dev MinIO 에서 수동 절차로 별도 확인한다.
     */
    @Test
    void worm_object_has_compliance_retention_metadata() throws Exception {
        String key = "anchors/test/" + System.currentTimeMillis() + ".json";
        byte[] payload = ("{\"date\":\"2026-05-10\",\"merkle_root\":\"abc\"}").getBytes();

        wrapper.uploadWithRetention(props.bucketAnchors(), key, payload, "application/json", 3650);

        Retention retention = minio.getObjectRetention(
                GetObjectRetentionArgs.builder().bucket(props.bucketAnchors()).object(key).build());

        assertThat(retention).isNotNull();
        assertThat(retention.mode()).isEqualTo(RetentionMode.COMPLIANCE);
        assertThat(retention.retainUntilDate()).isAfter(ZonedDateTime.now().plusDays(3640));
    }

    /** verifyLockConfiguration 이 COMPLIANCE 버킷에서 통과 */
    @Test
    void verifyLockConfiguration_passes_for_compliance_bucket() {
        wrapper.verifyLockConfiguration(props.bucketAnchors(), RetentionMode.COMPLIANCE);
    }

    /** verifyLockConfiguration 이 unlocked 버킷에서 fail-fast */
    @Test
    void verifyLockConfiguration_fails_for_unlocked_bucket() {
        // bucket-original 은 lock 없이 생성됨 (M7 에서 GOVERNANCE 전환 예정)
        assertThatThrownBy(() -> wrapper.verifyLockConfiguration(props.bucketOriginal(), RetentionMode.COMPLIANCE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(props.bucketOriginal());
    }
}
