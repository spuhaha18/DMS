package com.lab.edms.project;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
@Table(name = "retention_extension_outbox")
public class RetentionExtensionOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_code", nullable = false, length = 50)
    private String projectCode;

    @Column(name = "document_file_id", nullable = false)
    private Long documentFileId;

    @Column(name = "bucket", nullable = false, length = 100)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "new_retain_until", nullable = false)
    private Instant newRetainUntil;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static RetentionExtensionOutbox pending(String projectCode, Long documentFileId,
                                                    String bucket, String objectKey,
                                                    Instant newRetainUntil) {
        RetentionExtensionOutbox e = new RetentionExtensionOutbox();
        e.projectCode = projectCode;
        e.documentFileId = documentFileId;
        e.bucket = bucket;
        e.objectKey = objectKey;
        e.newRetainUntil = newRetainUntil;
        return e;
    }

    public void markSuccess() {
        this.status = "SUCCESS";
        this.processedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.status = "FAILED";
        this.processedAt = Instant.now();
        this.lastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
    }

    public void incrementAttempts(String error) {
        this.attempts++;
        this.lastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        this.status = "PENDING";
        this.lockedAt = null;
        this.lockedBy = null;
    }

    public Long getId() { return id; }
    public String getProjectCode() { return projectCode; }
    public Long getDocumentFileId() { return documentFileId; }
    public String getBucket() { return bucket; }
    public String getObjectKey() { return objectKey; }
    public Instant getNewRetainUntil() { return newRetainUntil; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
}
