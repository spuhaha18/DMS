package com.lab.edms.audit;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** audit_checkpoints 행 매핑 record (JdbcTemplate RowMapper 결과). */
public record AuditCheckpoint(
        Long id,
        LocalDate checkpointDate,
        String merkleRoot,
        long recordCount,
        Long firstLogId,
        Long lastLogId,
        String prevAnchorHash,
        String anchorHash,
        String minioKey,
        OffsetDateTime generatedAt
) {}
