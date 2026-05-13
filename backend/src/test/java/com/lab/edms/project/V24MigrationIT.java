package com.lab.edms.project;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
class V24MigrationIT {

    @Autowired JdbcTemplate jdbc;

    @Test
    void tableExists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_name = 'retention_extension_outbox'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void pendingStatusIndex() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes " +
                "WHERE tablename = 'retention_extension_outbox' " +
                "AND indexname = 'idx_outbox_pending'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void checkConstraintRejectsBogusStatus() {
        // 유효한 project_code와 document_file_id가 없으므로 FK 없이 제약만 테스트
        // status 컬럼의 CHECK 제약이 INVALID를 거부하는지 확인
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO retention_extension_outbox " +
                "(project_code, document_file_id, bucket, object_key, new_retain_until, status) " +
                "VALUES ('BOGUS_CODE', 99999999, 'bucket', 'key', now(), 'INVALID')"))
                .hasMessageContaining("chk_outbox_status");
    }

    @Test
    void foreignKeyToResearchProjects() {
        // 존재하지 않는 project_code로 삽입 시 FK 위반
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO retention_extension_outbox " +
                "(project_code, document_file_id, bucket, object_key, new_retain_until, status) " +
                "VALUES ('NON_EXISTENT_PROJECT_XYZ', 99999999, 'bucket', 'key', now(), 'PENDING')"))
                .hasMessageContaining("retention_extension_outbox");
    }

    @Test
    void statusColumnDefaultIsPending() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name = 'retention_extension_outbox' " +
                "AND column_name = 'status' " +
                "AND column_default LIKE '%PENDING%'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void byProjectIndexExists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes " +
                "WHERE tablename = 'retention_extension_outbox' " +
                "AND indexname = 'idx_outbox_by_project'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
