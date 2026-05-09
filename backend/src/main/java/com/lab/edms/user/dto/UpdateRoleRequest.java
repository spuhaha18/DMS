package com.lab.edms.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(
        @JsonProperty("role_name") @NotBlank @Size(max = 100) String roleName,
        @Size(max = 1000) String description
) {}
