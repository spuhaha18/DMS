package com.lab.edms.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record VerifyRequest(
        @JsonProperty("from_date") @NotNull LocalDate fromDate,
        @JsonProperty("to_date")   @NotNull LocalDate toDate
) {}
