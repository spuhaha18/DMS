package com.lab.edms.audit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * MinIO 에 저장되는 일별 앵커 파일 JSON.
 * 키 순서 고정(canonical) — 파일 SHA-256 으로 재검증 시 결정성 보장.
 */
@JsonPropertyOrder({
        "date", "merkle_root", "record_count",
        "first_id", "last_id",
        "prev_anchor_hash", "anchor_hash",
        "generated_at"
})
public record AnchorJson(
        @JsonProperty("date")             LocalDate date,
        @JsonProperty("merkle_root")      String merkleRoot,
        @JsonProperty("record_count")     long recordCount,
        @JsonProperty("first_id")         Long firstId,
        @JsonProperty("last_id")          Long lastId,
        @JsonProperty("prev_anchor_hash") String prevAnchorHash,
        @JsonProperty("anchor_hash")      String anchorHash,
        @JsonProperty("generated_at")     OffsetDateTime generatedAt
) {
    /** anchor_hash 계산: SHA-256(prev|merkle|date|count|first|last). null 은 빈 문자열. */
    public static String computeAnchorHash(String prevAnchorHash, String merkleRoot,
                                           LocalDate date, long recordCount,
                                           Long firstId, Long lastId) {
        String payload = prevAnchorHash
                + "|" + merkleRoot
                + "|" + date.toString()
                + "|" + recordCount
                + "|" + (firstId == null ? "" : firstId)
                + "|" + (lastId  == null ? "" : lastId);
        return HashChainSerializer.sha256Hex(payload);
    }
}
