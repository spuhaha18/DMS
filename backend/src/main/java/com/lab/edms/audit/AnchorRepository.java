package com.lab.edms.audit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * audit_checkpoints 접근. 쓰기 = audit_role(auditJdbcTemplate), 읽기 = app_role(primary jdbcTemplate).
 */
@Repository
public class AnchorRepository {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate auditJdbcTemplate;

    private static final RowMapper<AuditCheckpoint> MAPPER = (ResultSet rs, int rn) -> new AuditCheckpoint(
            rs.getLong("id"),
            rs.getObject("checkpoint_date", LocalDate.class),
            rs.getString("merkle_root"),
            rs.getLong("record_count"),
            getNullableLong(rs, "first_log_id"),
            getNullableLong(rs, "last_log_id"),
            rs.getString("prev_anchor_hash"),
            rs.getString("anchor_hash"),
            rs.getString("minio_key"),
            toOffset(rs.getTimestamp("generated_at"))
    );

    public AnchorRepository(JdbcTemplate jdbcTemplate,
                            @Qualifier("auditJdbcTemplate") JdbcTemplate auditJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditJdbcTemplate = auditJdbcTemplate;
    }

    public Optional<AuditCheckpoint> findLatest() {
        List<AuditCheckpoint> rows = jdbcTemplate.query(
                "SELECT * FROM audit_checkpoints ORDER BY checkpoint_date DESC LIMIT 1", MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<AuditCheckpoint> findByDate(LocalDate date) {
        List<AuditCheckpoint> rows = jdbcTemplate.query(
                "SELECT * FROM audit_checkpoints WHERE checkpoint_date = ?", MAPPER, date);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<AuditCheckpoint> findByRange(LocalDate from, LocalDate to) {
        return jdbcTemplate.query(
                "SELECT * FROM audit_checkpoints WHERE checkpoint_date BETWEEN ? AND ? "
              + "ORDER BY checkpoint_date", MAPPER, from, to);
    }

    /** 쓰기는 audit_role. INSERT-only 정책과 일관. */
    public void insert(AuditCheckpoint cp) {
        auditJdbcTemplate.update(
                "INSERT INTO audit_checkpoints "
              + "(checkpoint_date, merkle_root, record_count, first_log_id, last_log_id, "
              + " prev_anchor_hash, anchor_hash, minio_key) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                cp.checkpointDate(),
                cp.merkleRoot(),
                cp.recordCount(),
                cp.firstLogId(),
                cp.lastLogId(),
                cp.prevAnchorHash(),
                cp.anchorHash(),
                cp.minioKey()
        );
    }

    public record DayWindow(List<String> thisHashes, Long firstId, Long lastId, long count) {}

    /**
     * 지정 KST 일자의 audit_logs this_hash 들을 id 순으로 조회.
     * server_ts 는 UTC TIMESTAMPTZ 저장 — Postgres 가 'Asia/Seoul' 시간대로 캐스팅해 비교.
     */
    public DayWindow findKstDayWindow(LocalDate kstDate) {
        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT this_hash FROM audit_logs "
              + "WHERE server_ts >= (?::date)::timestamp AT TIME ZONE 'Asia/Seoul' "
              + "  AND server_ts <  ((?::date) + INTERVAL '1 day')::timestamp AT TIME ZONE 'Asia/Seoul' "
              + "ORDER BY id",
                String.class, kstDate.toString(), kstDate.toString());

        if (hashes.isEmpty()) {
            return new DayWindow(hashes, null, null, 0L);
        }

        Long firstId = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM audit_logs "
              + "WHERE server_ts >= (?::date)::timestamp AT TIME ZONE 'Asia/Seoul' "
              + "  AND server_ts <  ((?::date) + INTERVAL '1 day')::timestamp AT TIME ZONE 'Asia/Seoul'",
                Long.class, kstDate.toString(), kstDate.toString());
        Long lastId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM audit_logs "
              + "WHERE server_ts >= (?::date)::timestamp AT TIME ZONE 'Asia/Seoul' "
              + "  AND server_ts <  ((?::date) + INTERVAL '1 day')::timestamp AT TIME ZONE 'Asia/Seoul'",
                Long.class, kstDate.toString(), kstDate.toString());
        return new DayWindow(hashes, firstId, lastId, hashes.size());
    }

    /**
     * audit_logs 의 가장 이른 KST 일자 (catchup 시작점 결정용). 비어 있으면 Optional.empty.
     */
    public Optional<LocalDate> findEarliestKstAuditDate() {
        List<LocalDate> rows = jdbcTemplate.query(
                "SELECT (server_ts AT TIME ZONE 'Asia/Seoul')::date AS d "
              + "FROM audit_logs ORDER BY id ASC LIMIT 1",
                (rs, rn) -> rs.getObject("d", LocalDate.class));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static Long getNullableLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static OffsetDateTime toOffset(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
