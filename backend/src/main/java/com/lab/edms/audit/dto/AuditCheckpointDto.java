package com.lab.edms.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lab.edms.audit.AuditCheckpoint;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** OAS 스키마 #/components/schemas/AuditCheckpoint 와 1:1. */
public record AuditCheckpointDto(
        Long id,
        @JsonProperty("checkpoint_date") LocalDate checkpointDate,
        @JsonProperty("merkle_root")     String merkleRoot,
        @JsonProperty("record_count")    long recordCount,
        @JsonProperty("first_log_id")    Long firstLogId,
        @JsonProperty("last_log_id")     Long lastLogId,
        @JsonProperty("minio_key")       String minioKey,
        @JsonProperty("generated_at")    OffsetDateTime generatedAt
) {
    public static AuditCheckpointDto from(AuditCheckpoint cp) {
        return new AuditCheckpointDto(cp.id(), cp.checkpointDate(), cp.merkleRoot(),
                cp.recordCount(), cp.firstLogId(), cp.lastLogId(),
                cp.minioKey(), cp.generatedAt());
    }
}
