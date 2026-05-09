package com.lab.edms.document.dto;

public record CreateDocumentResponse(Long docId, Long versionId, String docNumber, String state) {}
