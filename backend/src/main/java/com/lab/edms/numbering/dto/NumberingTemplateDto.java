package com.lab.edms.numbering.dto;

import java.time.OffsetDateTime;

public record NumberingTemplateDto(
    Long id,
    Long categoryId,
    String categoryCode,
    String formatPattern,
    String counterScope,
    OffsetDateTime updatedAt
) {}
