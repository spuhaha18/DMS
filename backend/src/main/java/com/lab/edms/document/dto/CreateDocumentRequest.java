package com.lab.edms.document.dto;

public record CreateDocumentRequest(
        String categoryCode,
        String department,
        String projectCode,
        String title,
        boolean confidential
) {}
