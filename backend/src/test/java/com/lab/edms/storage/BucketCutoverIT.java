package com.lab.edms.storage;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.document.DocumentVersion;
import io.minio.GetObjectLockConfigurationArgs;
import io.minio.MinioClient;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
class BucketCutoverIT {

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
    void newOriginalV2_andRendition_haveGovernanceLock() throws Exception {
        wrapper.ensureBuckets();
        var v2Cfg = minioClient.getObjectLockConfiguration(
                GetObjectLockConfigurationArgs.builder().bucket(props.bucketOriginalV2()).build());
        var rendCfg = minioClient.getObjectLockConfiguration(
                GetObjectLockConfigurationArgs.builder().bucket(props.bucketRendition()).build());
        assertThat(v2Cfg.mode()).isEqualTo(RetentionMode.GOVERNANCE);
        assertThat(rendCfg.mode()).isEqualTo(RetentionMode.GOVERNANCE);
    }

    @Test
    void getOriginalBucket_legacyVersion_returnsLegacyBucket() {
        DocumentVersion legacy = new DocumentVersion();
        legacy.setCreatedAt(OffsetDateTime.parse("2025-01-01T00:00:00Z"));
        assertThat(wrapper.getOriginalBucket(legacy)).isEqualTo(props.bucketOriginal());
    }

    @Test
    void getOriginalBucket_newVersion_returnsV2Bucket() {
        DocumentVersion newVer = new DocumentVersion();
        newVer.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        assertThat(wrapper.getOriginalBucket(newVer)).isEqualTo(props.bucketOriginalV2());
    }
}
