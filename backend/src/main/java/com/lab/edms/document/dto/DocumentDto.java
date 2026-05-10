package com.lab.edms.document.dto;

import com.lab.edms.document.Document;

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
        String createdAt
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
                d.getCreatedAt() != null ? d.getCreatedAt().toString() : null
        );
    }
}
