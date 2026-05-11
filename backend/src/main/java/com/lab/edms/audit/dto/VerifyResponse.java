package com.lab.edms.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VerifyResponse(
        boolean valid,
        @JsonProperty("checked_records") long checkedRecords,
        @JsonProperty("first_broken_id") Long firstBrokenId,
        String details
) {}
