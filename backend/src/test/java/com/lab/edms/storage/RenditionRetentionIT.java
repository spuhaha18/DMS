package com.lab.edms.storage;

import com.lab.edms.document.Document;
import io.minio.*;
import io.minio.messages.RetentionMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RenditionRetentionIT {

    @Autowired MinioClientWrapper wrapper;
    @Autowired MinioClient minioClient;
    @Autowired RetentionResolver resolver;
    @Autowired MinioProperties props;

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
