package com.lab.edms.m3;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.audit.HashChainSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3 regression: audit_log hash-chain integrity after M3 actions are appended.
 *
 * Verifies:
 *  1. The first log row uses GENESIS_HASH as prev_hash.
 *  2. Each subsequent row's prev_hash equals the previous row's this_hash (no forks).
 *  3. M3-specific audit actions (DOCUMENT_CREATED, DEPARTMENT_CREATED) are accepted by AuditService.
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
class M3HashChainIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired AuditService auditService;

    @BeforeEach
    void clearAuditLogs() {
        // primary datasource has superuser privilege → can TRUNCATE despite audit_role REVOKE
        jdbc.execute("TRUNCATE TABLE audit_logs RESTART IDENTITY");
    }

    @Test
    void audit_hash_chain_무결성() {
        // Append a representative set of M3 actions
        auditService.log(new AuditEvent("admin", AuditAction.DEPARTMENT_CREATED,
            "DEPARTMENT", "QC", null, "{\"dept_code\":\"QC\"}", null, "127.0.0.1",
            OffsetDateTime.now()));

        auditService.log(new AuditEvent("admin", AuditAction.DOCUMENT_CATEGORY_CREATED,
            "DOCUMENT_CATEGORY", "1", null, "{\"code\":\"SOP\"}", null, "127.0.0.1",
            OffsetDateTime.now()));

        auditService.log(new AuditEvent("author1", AuditAction.DOCUMENT_CREATED,
            "DOCUMENT", "42", null, "{\"doc_number\":\"SOP-QC-001\"}", null, "10.0.0.1",
            OffsetDateTime.now()));

        auditService.log(new AuditEvent("author1", AuditAction.DOCUMENT_FILE_UPLOADED,
            "DOCUMENT_FILE", "42", null, "{\"file\":\"test.docx\"}", null, "10.0.0.1",
            OffsetDateTime.now()));

        // Retrieve all rows ordered by id
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, prev_hash, this_hash FROM audit_logs ORDER BY id ASC");

        assertThat(rows).hasSize(4);

        // First row: prev_hash must be the GENESIS sentinel
        assertThat(rows.get(0).get("prev_hash"))
            .as("First audit row must use GENESIS_HASH as prev_hash")
            .isEqualTo(HashChainSerializer.GENESIS_HASH);

        // All hashes must be 64-char hex (SHA-256)
        for (Map<String, Object> row : rows) {
            assertThat((String) row.get("this_hash"))
                .as("this_hash for id=%s must be 64 hex chars", row.get("id"))
                .hasSize(64);
        }

        // Chain integrity: row[i].prev_hash == row[i-1].this_hash
        for (int i = 1; i < rows.size(); i++) {
            String prevThisHash  = (String) rows.get(i - 1).get("this_hash");
            String currPrevHash  = (String) rows.get(i).get("prev_hash");
            assertThat(currPrevHash)
                .as("Row %d prev_hash should equal row %d this_hash", i, i - 1)
                .isEqualTo(prevThisHash);
        }
    }

    @Test
    void m3_audit_actions_수락됨() {
        // Smoke-check that all new M3 AuditAction enum values are accepted without exception
        AuditAction[] m3Actions = {
            AuditAction.DEPARTMENT_CREATED,
            AuditAction.DEPARTMENT_UPDATED,
            AuditAction.DOCUMENT_CATEGORY_CREATED,
            AuditAction.DOCUMENT_CATEGORY_UPDATED,
            AuditAction.NUMBERING_TEMPLATE_CREATED,
            AuditAction.NUMBERING_TEMPLATE_UPDATED,
            AuditAction.DOCUMENT_CREATED,
            AuditAction.DOCUMENT_FILE_UPLOADED,
            AuditAction.DOCUMENT_METADATA_UPDATED,
            AuditAction.DOCUMENT_VIEWED,
        };

        for (AuditAction action : m3Actions) {
            auditService.log(new AuditEvent("admin", action,
                "TEST", "1", null, null, null, "127.0.0.1", OffsetDateTime.now()));
        }

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class);
        assertThat(count).isEqualTo(m3Actions.length);
    }
}
