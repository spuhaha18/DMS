package com.lab.edms.numbering.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertNumberingTemplateRequest(
    @NotNull Long categoryId,
    @NotBlank @Size(max = 200) String formatPattern,
    @NotBlank String counterScope
) {}
