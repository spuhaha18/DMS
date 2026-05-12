package com.lab.edms.storage;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.document.Document;
import io.minio.*;
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

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
class RenditionRetentionIT {

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
    @Autowired RetentionResolver resolver;
    @Autowired MinioProperties props;

    @BeforeEach
    void setUp() {
        wrapper.ensureBuckets();
    }

    @Test
    void defaultResolver_returns10Years() {
        Document doc = new Document();
        int years = resolver.resolveYears(doc);
        assertThat(years).isEqualTo(10);
    }

    @Test
    void rendition_uploaded_with_compliance_10y() throws Exception {
        int years = 10;
        var result = wrapper.uploadWithRetention(
                props.bucketRendition(),
                "test-retention/rendition-" + System.currentTimeMillis() + ".pdf",
                "PDF-bytes".getBytes(), "application/pdf",
                years * 365);
        var ret = minioClient.getObjectRetention(
                GetObjectRetentionArgs.builder()
                        .bucket(result.bucket())
                        .object(result.key())
                        .build());
        assertThat(ret.mode()).isEqualTo(RetentionMode.COMPLIANCE);
        long days = ChronoUnit.DAYS.between(ZonedDateTime.now(ZoneOffset.UTC), ret.retainUntilDate());
        assertThat(days).isBetween(3640L, 3660L);
    }
}
