package com.lab.edms.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertDepartmentRequest(
    @NotBlank @Size(max = 50) String deptCode,
    @NotBlank @Size(max = 100) String primaryName,
    Boolean active
) {}
