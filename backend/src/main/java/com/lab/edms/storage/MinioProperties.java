package com.lab.edms.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucketOriginal,        // legacy (read-only after M7 cutover)
        String bucketOriginalV2,      // M7 신규 GOVERNANCE 본문
        String bucketRendition,       // M7 신규 GOVERNANCE PDF rendition
        String bucketAnchors          // M5 COMPLIANCE
) {}
