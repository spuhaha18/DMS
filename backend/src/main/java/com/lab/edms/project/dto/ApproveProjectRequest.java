package com.lab.edms.project.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ApproveProjectRequest(
        @NotNull LocalDate approvalDate
) {}
