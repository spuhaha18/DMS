package com.lab.edms.document.dto;

/**
 * DTO for document file metadata.
 *
 * <p>M7.1: adds {@code renditionKind} and {@code stepNumber} to expose which PDF
 * rendition the file row represents (INITIAL/STAMPED/EFFECTIVE) and, for STAMPED,
 * which workflow step produced it.</p>
 *
 * <ul>
 *   <li>{@code fileType} — DOCX/XLSX/PPTX/PDF (logical format) for ORIGINAL uploads,
 *       or the DB role ("ORIGINAL"/"RENDITION") depending on caller.</li>
 *   <li>{@code renditionKind} — INITIAL / STAMPED / EFFECTIVE for RENDITION rows; null otherwise.</li>
 *   <li>{@code stepNumber} — non-null only when {@code renditionKind == STAMPED}.</li>
 * </ul>
 */
public record DocumentFileDto(
        Long id,
        Long versionId,
        String fileType,
        String minioKey,
        String fileName,
        Long fileSizeBytes,
        String contentType,
        String sha256Hash,
        String uploadedAt,
        String renditionKind,
        Integer stepNumber
) {}
