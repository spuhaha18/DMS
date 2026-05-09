package com.lab.edms.category.dto;

import java.time.OffsetDateTime;

public record CategoryDto(
    Long id,
    String categoryCode,
    String categoryName,
    String description,
    int reviewPeriodMonths,
    boolean qaMandatory,
    boolean active,
    OffsetDateTime createdAt
) {}
