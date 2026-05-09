package com.lab.edms.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public record CreateUserRequest(
        @JsonProperty("user_id")
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9._-]{2,50}$",
                 message = "user_id must be 2-50 chars: letters, digits, '.', '-', '_'")
        String userId,

        @JsonProperty("full_name")
        @NotBlank @Size(max = 100)
        String fullName,

        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank @Size(max = 50)
        String department,

        @Size(max = 50)
        String title,

        @NotBlank @Size(min = 8, max = 100)
        String password,

        @JsonProperty("role_codes")
        @NotEmpty(message = "at least one role is required")
        List<@NotBlank String> roleCodes,

        @JsonProperty("valid_from") LocalDate validFrom,
        @JsonProperty("valid_until") LocalDate validUntil
) {}
