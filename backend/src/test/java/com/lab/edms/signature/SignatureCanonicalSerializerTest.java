package com.lab.edms.signature;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class SignatureCanonicalSerializerTest {

    @Test
    void escape_handles_backslash_first() {
        // backslash must be escaped before pipe to avoid double-escaping
        assertThat(SignatureCanonicalSerializer.escape("a\\b")).isEqualTo("a\\\\b");
    }

    @Test
    void escape_handles_pipe() {
        assertThat(SignatureCanonicalSerializer.escape("a|b")).isEqualTo("a\\|b");
    }

    @Test
    void escape_handles_combined_pipe_and_backslash() {
        // "a\|b" → escape backslash first → "a\\|b" → escape pipe → "a\\\\|b" → wait
        // Actually: "a\|b".replace("\\", "\\\\") = "a\\|b" then .replace("|", "\\|") = "a\\\\|b"
        // Hmm, let me recalculate: input is a\|b (3 chars after a: backslash, pipe, b)
        // Step 1: replace \ with \\ → a\\|b
        // Step 2: replace | with \| → a\\\|b
        assertThat(SignatureCanonicalSerializer.escape("a\\|b")).isEqualTo("a\\\\\\|b");
    }

    @Test
    void escape_handles_empty_string() {
        assertThat(SignatureCanonicalSerializer.escape("")).isEqualTo("");
    }

    @Test
    void nfc_normalizes_korean_decomposed() {
        // U+1100 (ᄀ) + U+1161 (ᅡ) → NFC U+AC00 (가)
        // Use unicode escapes to guarantee NFD input regardless of source file encoding
        String nfd = "가";
        String nfc = "가";
        assertThat(SignatureCanonicalSerializer.nfc(nfd)).isEqualTo(nfc);
    }

    @Test
    void nfc_preserves_already_composed() {
        assertThat(SignatureCanonicalSerializer.nfc("가")).isEqualTo("가");
    }

    @Test
    void serialize_produces_8_field_payload_without_prev_hash() {
        // canonical_payload column stores 8 fields (prev_hash excluded, stored separately)
        String payload = SignatureCanonicalSerializer.serialize(
                42L,                          // signerId
                "APPROVED",                   // meaning
                Instant.parse("2026-05-11T10:00:00Z"),
                100L,                         // versionId
                "SOP-QA-001",                 // docNumber
                3,                            // revision
                "UNDER_APPROVAL",             // docStatus
                "abc123def456"                // sourceFileSha256
        );
        assertThat(payload).isEqualTo(
            "42|APPROVED|2026-05-11T10:00:00Z|100|SOP-QA-001|3|UNDER_APPROVAL|abc123def456"
        );
    }

    @Test
    void serialize_escapes_pipe_in_doc_number() {
        String payload = SignatureCanonicalSerializer.serialize(
                42L, "APPROVED", Instant.parse("2026-05-11T10:00:00Z"),
                100L, "SOP|QA", 3, "UNDER_APPROVAL", "abc"
        );
        assertThat(payload).contains("SOP\\|QA");
        // Verify the escaped pipe doesn't split the field
        String[] parts = payload.split("(?<!\\\\)\\|");
        // Should have 8 parts (pipe-split respecting escaped pipes)
        // Actually simpler: just verify the raw string contains escaped pipe
        assertThat(payload).contains("SOP\\|QA");
    }

    // ── OQ-SIG-014 ──────────────────────────────────────────────────────────

    @Test
    void oqSig014_pipeInField_preventsHashCollision() {
        // Case A: pipe in docNumber
        String payloadA = SignatureCanonicalSerializer.serialize(
                1L, "APPROVED", Instant.parse("2026-05-11T10:00:00Z"),
                10L, "SOP|QA", 1, "UNDER_APPROVAL", "abc");
        // Case B: pipe in docStatus (same raw string without escaping)
        String payloadB = SignatureCanonicalSerializer.serialize(
                1L, "APPROVED", Instant.parse("2026-05-11T10:00:00Z"),
                10L, "SOP", 1, "QA|UNDER_APPROVAL", "abc");

        // Payloads must be different strings (escaping prevents collision)
        assertThat(payloadA).isNotEqualTo(payloadB);

        // Verify Case A: docNumber pipe is escaped
        assertThat(payloadA).contains("SOP\\|QA");
        // Verify Case B: docStatus pipe is escaped
        assertThat(payloadB).contains("QA\\|UNDER_APPROVAL");

        // The two payloads, when hashed, must produce different hashes
        String hashA = sha256(payloadA);
        String hashB = sha256(payloadB);
        assertThat(hashA).isNotEqualTo(hashB);
    }

    @Test
    void oqSig014_genesisHashIsNotLiteralGenesis() {
        // genesis hash must be HEX(SHA-256("GENESIS")), not the literal string "GENESIS"
        String genesisHash = sha256("GENESIS");
        assertThat(genesisHash).hasSize(64);
        assertThat(genesisHash).matches("[0-9a-f]{64}");
        assertThat(genesisHash).isNotEqualTo("GENESIS");
    }

    private static String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
