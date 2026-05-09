package com.lab.edms.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisableUserRequest(
        @NotBlank @Size(min = 5, max = 500, message = "reason ≥5 chars required") String reason
) {}
