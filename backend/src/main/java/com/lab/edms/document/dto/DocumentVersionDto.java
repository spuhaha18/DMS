package com.lab.edms.document.dto;

import com.lab.edms.document.DocumentVersion;

public record DocumentVersionDto(
        Long id,
        Long documentId,
        Integer revision,
        String state,
        String title,
        String changeSummary,
        String reasonForChange,
        String sourceFileKey,
        String pdfStatus,
        String createdAt,
        String updatedAt
) {
    public static DocumentVersionDto fromEntity(DocumentVersion v) {
        return new DocumentVersionDto(
                v.getId(),
                v.getDocumentId(),
                v.getRevision(),
                v.getState(),
                v.getTitle(),
                v.getChangeSummary(),
                v.getReasonForChange(),
                v.getSourceFileKey(),
                v.getPdfStatus(),
                v.getCreatedAt() != null ? v.getCreatedAt().toString() : null,
                v.getUpdatedAt() != null ? v.getUpdatedAt().toString() : null
        );
    }
}
