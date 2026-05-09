package com.lab.edms.numbering.dto;

public record NumberingPreviewRequest(
    Long categoryId,
    String department,
    String projectCode
) {}
