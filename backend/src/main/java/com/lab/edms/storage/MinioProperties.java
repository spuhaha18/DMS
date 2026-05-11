package com.lab.edms.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucketOriginal,
        String bucketRendition,
        String bucketAnchors
) {}
