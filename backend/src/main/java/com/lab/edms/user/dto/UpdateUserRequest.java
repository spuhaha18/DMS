package com.lab.edms.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record UpdateUserRequest(
        @JsonProperty("full_name") @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 50) String department,
        @Size(max = 50) String title,
        @JsonProperty("valid_from") LocalDate validFrom,
        @JsonProperty("valid_until") LocalDate validUntil
) {}
