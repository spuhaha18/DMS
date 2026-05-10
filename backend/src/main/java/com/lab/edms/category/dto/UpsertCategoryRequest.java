package com.lab.edms.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertCategoryRequest(
    @NotBlank @Size(max = 20) String categoryCode,
    @NotBlank @Size(max = 100) String categoryName,
    String description,
    int reviewPeriodMonths,
    boolean qaMandatory,
    Boolean active
) {}
