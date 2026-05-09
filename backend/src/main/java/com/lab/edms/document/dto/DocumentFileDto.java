package com.lab.edms.document.dto;

public record DocumentFileDto(
        Long id,
        Long versionId,
        String fileType,
        String minioKey,
        String fileName,
        Long fileSizeBytes,
        String contentType,
        String sha256Hash,
        String uploadedAt
) {}
