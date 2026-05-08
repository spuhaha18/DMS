package com.lab.edms.audit;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class HashChainSerializerTest {

    @Test
    void genesisHash_isSha256OfLiteralGENESIS() {
        // SHA-256("GENESIS") = 901131d838b17aac0f7885b81e03cbdc9f5157a00343d30ab22083685ed1416a
        // Verified: printf 'GENESIS' | sha256sum
        assertThat(HashChainSerializer.GENESIS_HASH)
                .isEqualTo("901131d838b17aac0f7885b81e03cbdc9f5157a00343d30ab22083685ed1416a");
    }

    @Test
    void payload_concatenatesAllNineFieldsInDocumentedOrder() {
        AuditEvent e = new AuditEvent(
                "alice",
                AuditAction.USER_LOGIN_SUCCESS,
                "USER",
                "42",
                "{\"status\":\"ACTIVE\"}", null, "login reason",
                "10.0.0.1",
                OffsetDateTime.of(2026, 5, 8, 9, 30, 0, 0, ZoneOffset.UTC)
        );

        String payload = HashChainSerializer.payload("PREVHASH", e);

        // Format: prev|actor|action|type:id|server_ts_iso|before_value|after_value|reason|client_ip
        // Java 21's ISO_OFFSET_DATE_TIME always includes seconds, so "09:30:00Z" not "09:30Z"
        assertThat(payload).isEqualTo(
                "PREVHASH|alice|USER_LOGIN_SUCCESS|USER:42|2026-05-08T09:30:00Z|{\"status\":\"ACTIVE\"}||login reason|10.0.0.1");
    }

    @Test
    void payload_nullOptionalFields_useEmptyString() {
        AuditEvent e = new AuditEvent(
                "alice", AuditAction.USER_LOGIN_SUCCESS, "USER", "42",
                null, null, null, null,
                OffsetDateTime.of(2026, 5, 8, 9, 30, 0, 0, ZoneOffset.UTC)
        );
        String payload = HashChainSerializer.payload("P", e);
        // All null optional fields become empty string
        // Java 21's ISO_OFFSET_DATE_TIME always includes seconds, so "09:30:00Z" not "09:30Z"
        assertThat(payload).isEqualTo("P|alice|USER_LOGIN_SUCCESS|USER:42|2026-05-08T09:30:00Z||||");
    }

    @Test
    void sha256Hex_returns64LowercaseHexChars() {
        String h = HashChainSerializer.sha256Hex("anything");
        assertThat(h).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void sha256Hex_isDeterministic() {
        assertThat(HashChainSerializer.sha256Hex("x"))
                .isEqualTo(HashChainSerializer.sha256Hex("x"));
    }
}
