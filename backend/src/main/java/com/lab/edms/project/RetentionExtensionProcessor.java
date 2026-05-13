package com.lab.edms.project;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.storage.MinioClientWrapper;
import com.lab.edms.storage.RetentionShortenedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RetentionExtensionProcessor {

    private static final Logger log = LoggerFactory.getLogger(RetentionExtensionProcessor.class);
    private static final int MAX_ATTEMPTS = 5;

    private final RetentionExtensionOutboxRepository repo;
    private final MinioClientWrapper minio;
    private final AuditService auditService;

    public RetentionExtensionProcessor(RetentionExtensionOutboxRepository repo,
                                       MinioClientWrapper minio,
                                       AuditService auditService) {
        this.repo = repo;
        this.minio = minio;
        this.auditService = auditService;
    }

    @Transactional
    public void processOne(Long id) {
        RetentionExtensionOutbox e = repo.findById(id).orElseThrow();
        try {
            minio.extendRetention(e.getBucket(), e.getObjectKey(), e.getNewRetainUntil());
            e.markSuccess();
            log.info("retention.extended outbox={} bucket={} object={}", id, e.getBucket(), e.getObjectKey());
        } catch (RetentionShortenedException ex) {
            e.markFailed("retention_shortened: " + ex.getMessage());
            auditService.log(AuditEvent.of("system", AuditAction.RETENTION_EXTENSION_FAILED)
                    .entity("outbox", String.valueOf(id))
                    .reason("shortened_blocked: " + ex.getMessage())
                    .build());
            log.warn("retention.shortened_blocked outbox={} error={}", id, ex.getMessage());
        } catch (Exception ex) {
            e.incrementAttempts(ex.getMessage());
            if (e.getAttempts() >= MAX_ATTEMPTS) {
                e.markFailed("max_retries: " + ex.getMessage());
                auditService.log(AuditEvent.of("system", AuditAction.RETENTION_EXTENSION_FAILED)
                        .entity("outbox", String.valueOf(id))
                        .reason("dead_letter: " + ex.getMessage())
                        .build());
                log.error("retention.dead_letter outbox={} attempts={}", id, e.getAttempts(), ex);
            } else {
                log.warn("retention.retry outbox={} attempts={} error={}", id, e.getAttempts(), ex.getMessage());
            }
        }
        repo.save(e);
    }
}
