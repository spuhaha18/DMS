package com.lab.edms.signature;

import java.text.Normalizer;
import java.time.Instant;

/**
 * DS §8.1 canonical_payload 직렬화기.
 *
 * 직렬화 형식 (8필드, prev_hash 제외):
 *   signer_id | meaning | signed_at_iso | version_id | doc_number |
 *   revision | doc_status | source_file_sha256
 *
 * - String 필드: NFC 정규화 후 백슬래시 이스케이프 (\ → \\, | → \|)
 * - Numeric 필드: Long.toString() / Integer.toString()
 *
 * hash 계산 시: SHA-256(UTF-8(prev_hash + "|" + serialize(...)))
 * DB canonical_payload 컬럼: serialize(...) 결과만 저장 (prev_hash는 별도 컬럼)
 */
public final class SignatureCanonicalSerializer {

    private SignatureCanonicalSerializer() {}

    public static String nfc(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFC);
    }

    public static String escape(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replace("\\", "\\\\").replace("|", "\\|");
    }

    private static String norm(String s) {
        return escape(nfc(s));
    }

    public static String serialize(
            long signerId,
            String meaning,
            Instant signedAt,
            long versionId,
            String docNumber,
            int revision,
            String docStatus,
            String sourceFileSha256) {
        return Long.toString(signerId)
                + "|" + norm(meaning)
                + "|" + signedAt.toString()
                + "|" + Long.toString(versionId)
                + "|" + norm(docNumber)
                + "|" + Integer.toString(revision)
                + "|" + norm(docStatus)
                + "|" + norm(sourceFileSha256);
    }
}
