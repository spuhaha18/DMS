package com.lab.edms.audit;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
class AuditServiceIT {

    @Autowired AuditService auditService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JdbcTemplate auditJdbcTemplate;

    @Test
    void firstLog_usesGenesisAsPrevHash() {
        AuditEvent e = new AuditEvent("alice", AuditAction.USER_LOGIN_SUCCESS,
                "USER", "1", null, null, null, "10.0.0.1", OffsetDateTime.now());

        auditService.log(e);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT actor_user_id, action, prev_hash, this_hash FROM audit_logs ORDER BY id LIMIT 1");
        assertThat(row.get("actor_user_id")).isEqualTo("alice");
        assertThat(row.get("action")).isEqualTo("USER_LOGIN_SUCCESS");
        assertThat(row.get("prev_hash")).isEqualTo(HashChainSerializer.GENESIS_HASH);
        assertThat(row.get("this_hash")).asString().hasSize(64);
    }

    @Test
    void secondLog_chainsToFirstLogsThisHash() {
        auditService.log(new AuditEvent("a", AuditAction.USER_LOGIN_SUCCESS,
                "USER", "1", null, null, null, "10.0.0.1", OffsetDateTime.now()));
        auditService.log(new AuditEvent("a", AuditAction.USER_LOGOUT,
                "USER", "1", null, null, null, "10.0.0.1", OffsetDateTime.now()));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT prev_hash, this_hash FROM audit_logs ORDER BY id");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).get("prev_hash")).isEqualTo(rows.get(0).get("this_hash"));
    }

    @Test
    void auditRole_cannotUpdate() {
        auditService.log(new AuditEvent("a", AuditAction.USER_LOGIN_SUCCESS,
                "USER", "1", null, null, null, "10.0.0.1", OffsetDateTime.now()));

        assertThatThrownBy(() -> auditJdbcTemplate.update(
                "UPDATE audit_logs SET action = 'X' WHERE action = 'USER_LOGIN_SUCCESS'"))
                .hasMessageContaining("permission denied");
    }

    @Test
    void auditRole_cannotDelete() {
        auditService.log(new AuditEvent("a", AuditAction.USER_LOGIN_SUCCESS,
                "USER", "1", null, null, null, "10.0.0.1", OffsetDateTime.now()));

        assertThatThrownBy(() -> auditJdbcTemplate.update(
                "DELETE FROM audit_logs WHERE action = 'USER_LOGIN_SUCCESS'"))
                .hasMessageContaining("permission denied");
    }

    @Test
    void concurrentLogs_produceLinearChain_noFork() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int n = i;
            futures.add(pool.submit(() -> auditService.log(new AuditEvent(
                    "user" + n, AuditAction.USER_LOGIN_SUCCESS,
                    "USER", String.valueOf(n), null, null, null, "127.0.0.1",
                    OffsetDateTime.now()))));
        }
        for (Future<?> f : futures) f.get();
        pool.shutdown();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT prev_hash, this_hash FROM audit_logs ORDER BY id");
        assertThat(rows).hasSize(threads);
        for (int i = 1; i < rows.size(); i++) {
            assertThat(rows.get(i).get("prev_hash"))
                    .as("row %d prev_hash must equal row %d this_hash", i, i - 1)
                    .isEqualTo(rows.get(i - 1).get("this_hash"));
        }
    }
}
