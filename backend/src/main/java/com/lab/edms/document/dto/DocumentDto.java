package com.lab.edms.document.dto;

import com.lab.edms.document.Document;

/**
 * DTO for document metadata.
 *
 * <p>M7.1: adds {@code pdfStatus} — the PDF pipeline state machine value
 * (PENDING_CONVERSION / CONVERTED / STAMPING / STAMPED / WATERMARKING /
 * EFFECTIVE_STAMPED / CONVERSION_FAILED / STAMP_FAILED). Null for documents
 * that never started the PDF pipeline.</p>
 */
public record DocumentDto(
        Long id,
        String docNumber,
        Long categoryId,
        String categoryCode,
        String department,
        String projectCode,
        String title,
        Long ownerId,
        boolean confidential,
        String createdAt,
        String pdfStatus
) {
    public static DocumentDto fromEntity(Document d, String categoryCode) {
        return new DocumentDto(
                d.getId(),
                d.getDocNumber(),
                d.getCategoryId(),
                categoryCode,
                d.getDepartment(),
                d.getProjectCode(),
                d.getTitle(),
                d.getOwnerId(),
                d.isConfidential(),
                d.getCreatedAt() != null ? d.getCreatedAt().toString() : null,
                d.getPdfStatus()
        );
    }
}
