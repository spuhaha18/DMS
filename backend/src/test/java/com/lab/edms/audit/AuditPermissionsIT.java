package com.lab.edms.audit;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OQ-AUD-001~005 자동화.
 * audit_logs/signature_manifests/audit_checkpoints 모두 audit_role 만 INSERT,
 * 어떤 역할도 UPDATE/DELETE 불가임을 회귀로 보장한다.
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
class AuditPermissionsIT {

    @Autowired @Qualifier("auditJdbcTemplate") JdbcTemplate auditJdbcTemplate;

    /** OQ-AUD-001 (재확인): audit_logs UPDATE 거부 (V2 정책 회귀) */
    @Test
    void auditLogs_update_denied() {
        assertThatThrownBy(() -> auditJdbcTemplate.update(
                "UPDATE audit_logs SET action = 'TAMPERED' WHERE id = 1"))
                .isInstanceOf(DataAccessException.class)
                .cause()
                .hasMessageContaining("permission denied");
    }

    /** OQ-AUD-002 (재확인): audit_logs DELETE 거부 */
    @Test
    void auditLogs_delete_denied() {
        assertThatThrownBy(() -> auditJdbcTemplate.update(
                "DELETE FROM audit_logs WHERE id = 1"))
                .isInstanceOf(DataAccessException.class)
                .cause()
                .hasMessageContaining("permission denied");
    }

    /** OQ-AUD-003 (신규): signature_manifests UPDATE 거부 (V18 보강 확인) */
    @Test
    void signatureManifests_update_denied() {
        assertThatThrownBy(() -> auditJdbcTemplate.update(
                "UPDATE signature_manifests SET this_hash = 'TAMPERED' WHERE id = 1"))
                .isInstanceOf(DataAccessException.class)
                .cause()
                .hasMessageContaining("permission denied");
    }

    /** OQ-AUD-004 (신규): signature_manifests DELETE 거부 */
    @Test
    void signatureManifests_delete_denied() {
        assertThatThrownBy(() -> auditJdbcTemplate.update(
                "DELETE FROM signature_manifests WHERE id = 1"))
                .isInstanceOf(DataAccessException.class)
                .cause()
                .hasMessageContaining("permission denied");
    }

    /** OQ-AUD-005 (신규): audit_checkpoints UPDATE 거부 (V18 신규 테이블) */
    @Test
    void auditCheckpoints_update_denied() {
        assertThatThrownBy(() -> auditJdbcTemplate.update(
                "UPDATE audit_checkpoints SET merkle_root = 'TAMPERED' WHERE id = 1"))
                .isInstanceOf(DataAccessException.class)
                .cause()
                .hasMessageContaining("permission denied");
    }

    /** OQ-AUD-005b (신규): audit_checkpoints DELETE 거부 */
    @Test
    void auditCheckpoints_delete_denied() {
        assertThatThrownBy(() -> auditJdbcTemplate.update(
                "DELETE FROM audit_checkpoints WHERE id = 1"))
                .isInstanceOf(DataAccessException.class)
                .cause()
                .hasMessageContaining("permission denied");
    }

    /** OQ-AUD-005c (보강): audit_checkpoints INSERT 는 audit_role 로 가능 */
    @Test
    void auditCheckpoints_insert_allowed_for_audit_role() {
        int rows = auditJdbcTemplate.update(
                "INSERT INTO audit_checkpoints "
              + "(checkpoint_date, merkle_root, record_count, first_log_id, last_log_id, "
              + " prev_anchor_hash, anchor_hash, minio_key) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                java.sql.Date.valueOf("2099-12-31"),
                "0".repeat(64),
                0L, null, null,
                "0".repeat(64), "1".repeat(64),
                "anchors/2099/12/20991231.json");
        assertThat(rows).isEqualTo(1);
    }
}
