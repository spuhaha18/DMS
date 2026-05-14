package com.lab.edms.search;

import java.time.OffsetDateTime;

public record SearchResultDto(
        Long documentId,
        Long versionId,
        String docNumber,
        String title,
        String state,
        String categoryCode,
        String department,
        OffsetDateTime effectiveDate,
        String authorUserId,
        float rank
) {}
