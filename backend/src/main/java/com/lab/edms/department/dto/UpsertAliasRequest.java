package com.lab.edms.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertAliasRequest(
    @NotBlank @Size(max = 100) String aliasName,
    @Size(max = 8) String locale
) {}
