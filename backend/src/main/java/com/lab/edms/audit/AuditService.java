package com.lab.edms.audit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;

@Service
public class AuditService {

    private final JdbcTemplate auditJdbc;
    private final TransactionTemplate auditTx;

    static final long AUDIT_CHAIN_LOCK_KEY = 7_777_777_777L;

    public AuditService(
            @Qualifier("auditJdbcTemplate") JdbcTemplate auditJdbc,
            @Qualifier("auditDataSource") DataSource auditDataSource) {
        this.auditJdbc = auditJdbc;
        DataSourceTransactionManager tm = new DataSourceTransactionManager(auditDataSource);
        this.auditTx = new TransactionTemplate(tm);
        this.auditTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Appends an audit event to the hash chain. Uses an explicit TransactionTemplate
     * bound to the audit DataSource (audit_role) so:
     * 1. The advisory lock is held for the entire transaction, preventing chain forks.
     * 2. This transaction commits independently of any outer (JPA/primary) transaction,
     *    preserving the GxP audit trail even if the calling transaction rolls back.
     */
    public void log(AuditEvent event) {
        auditTx.execute(status -> {
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
            return null;
        });
    }
}
