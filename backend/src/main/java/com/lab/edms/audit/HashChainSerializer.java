package com.lab.edms.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;

public final class HashChainSerializer {

    public static final String GENESIS_HASH = sha256Hex("GENESIS");

    private HashChainSerializer() {}

    /**
     * DS-AUD-001 payload format:
     *   prev|actor|action|type:id|server_ts_iso|before_value|after_value|reason|client_ip
     *
     * All 9 fields included so tampering with any field breaks chain verification.
     * Null fields serialize as empty string.
     */
    public static String payload(String prevHash, AuditEvent e) {
        String ts       = e.serverTs().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String entityId = nvl(e.entityId());
        String actor    = nvl(e.actorUserId());
        String before   = nvl(e.beforeValue());
        String after    = nvl(e.afterValue());
        String reason   = nvl(e.reason());
        String ip       = nvl(e.clientIp());
        return prevHash + "|" + actor + "|" + e.action().name() + "|"
                + e.entityType() + ":" + entityId + "|" + ts
                + "|" + before + "|" + after + "|" + reason + "|" + ip;
    }

    private static String nvl(String s) { return s == null ? "" : s; }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
