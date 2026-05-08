package com.lab.edms.audit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

@Service
public class AuditService {

    private final JdbcTemplate auditJdbc;

    public AuditService(@Qualifier("auditJdbcTemplate") JdbcTemplate auditJdbc) {
        this.auditJdbc = auditJdbc;
    }

    static final long AUDIT_CHAIN_LOCK_KEY = 7_777_777_777L;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditEvent event) {
        // Serialize concurrent inserts: only one transaction builds the chain at a time.
        auditJdbc.execute("SELECT pg_advisory_xact_lock(" + AUDIT_CHAIN_LOCK_KEY + ")");

        String prevHash = auditJdbc.query(
                "SELECT this_hash FROM audit_logs ORDER BY id DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : HashChainSerializer.GENESIS_HASH);

        String payload = HashChainSerializer.payload(prevHash, event);
        String thisHash = HashChainSerializer.sha256Hex(payload);

        auditJdbc.update(
                "INSERT INTO audit_logs " +
                "(actor_user_id, action, entity_type, entity_id, before_value, after_value, " +
                " reason, client_ip, server_ts, prev_hash, this_hash) " +
                "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)",
                event.actorUserId(),
                event.action().name(),
                event.entityType(),
                event.entityId(),
                event.beforeValue(),
                event.afterValue(),
                event.reason(),
                event.clientIp(),
                Timestamp.from(event.serverTs().toInstant()),
                prevHash,
                thisHash
        );
    }
}
