package com.lab.edms.storage;

import com.lab.edms.document.DocumentVersion;
import io.minio.GetObjectLockConfigurationArgs;
import io.minio.MinioClient;
import io.minio.messages.RetentionMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BucketCutoverIT {

    @Autowired MinioClientWrapper wrapper;
    @Autowired MinioClient minioClient;
    @Autowired MinioProperties props;

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
