package com.lab.edms.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 50) String projectCode,
        @NotBlank @Size(max = 500) String projectName,
        @NotBlank @Size(max = 50) String typeCode
) {}
