package com.lab.edms.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateRolesRequest(
        @JsonProperty("role_codes") @NotEmpty List<@NotBlank String> roleCodes
) {}
