package com.lab.edms.pdf;

import java.time.Instant;

/**
 * Immutable stamp payload — serialized at sign() time, passed to async worker.
 * Worker MUST NOT re-query DB for these values to prevent tampering between
 * sign() commit and worker execution.
 */
public record StampPayload(
    Long signIntentId,
    Long versionId,
    int stepNumber,
    String signerUserId,
    String signerDisplayName,
    Instant signedAt,
    String meaning,
    String signatureBase64,
    String pubkeyFingerprint
) {}
