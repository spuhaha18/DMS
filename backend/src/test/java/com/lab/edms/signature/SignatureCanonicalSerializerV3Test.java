package com.lab.edms.signature;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class SignatureCanonicalSerializerV3Test {

    @Test
    void serializeV3_produces_9_field_pipe_separated() {
        String p = SignatureCanonicalSerializer.serializeV3(
            42L, "APPROVED", Instant.parse("2026-05-12T10:00:00Z"),
            100L, "SOP-QA-001", 3, "UNDER_APPROVAL",
            "abc123", "def456");
        assertThat(p).isEqualTo(
            "42|APPROVED|2026-05-12T10:00:00Z|100|SOP-QA-001|3|UNDER_APPROVAL|abc123|def456");
    }

    @Test
    void serializeV3_prefixMatchesV2() {
        String v2 = SignatureCanonicalSerializer.serialize(
            42L, "APPROVED", Instant.parse("2026-05-12T10:00:00Z"),
            100L, "SOP", 3, "UNDER_APPROVAL", "abc");
        String v3 = SignatureCanonicalSerializer.serializeV3(
            42L, "APPROVED", Instant.parse("2026-05-12T10:00:00Z"),
            100L, "SOP", 3, "UNDER_APPROVAL", "abc", "def");
        // v3는 v2 기반에 renditionSha 추가 — v2 payload를 prefix로 포함
        assertThat(v3).startsWith(v2);
    }

    @Test
    void serializeV3_escapes_pipe_in_meaning() {
        String p = SignatureCanonicalSerializer.serializeV3(
            1L, "APPRO|VED", Instant.parse("2026-01-01T00:00:00Z"),
            1L, "DOC-001", 1, "DRAFT",
            "sha256orig", "sha256rend");
        assertThat(p).contains("APPRO\\|VED");
    }

    @Test
    void serializeV3_fieldCount_is_nine() {
        String p = SignatureCanonicalSerializer.serializeV3(
            1L, "REVIEWED", Instant.parse("2026-01-01T00:00:00Z"),
            2L, "DOC-002", 0, "UNDER_REVIEW",
            "origHash", "rendHash");
        // 9개 필드 = 8개 구분자
        long pipeCount = p.chars().filter(c -> c == '|').count();
        assertThat(pipeCount).isEqualTo(8L);
    }
}
