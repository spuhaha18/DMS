package com.lab.edms.workqueue;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "work_queue")
public class WorkQueueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WorkQueueKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkQueueState state = WorkQueueState.OPEN;

    @Column(name = "assignee_user_id", nullable = false)
    private Long assigneeUserId;

    @Column(name = "delegated_from_user_id")
    private Long delegatedFromUserId;

    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "related_document_id")
    private Long relatedDocumentId;

    @Column(name = "related_version_id")
    private Long relatedVersionId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "link_path", nullable = false, length = 500)
    private String linkPath;

    @Column(nullable = false, length = 20)
    private String priority = "NORMAL";

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completed_by_user_id")
    private Long completedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void markDone(Long byUser, OffsetDateTime now) {
        if (this.state != WorkQueueState.OPEN) return;
        this.state = WorkQueueState.DONE;
        this.completedAt = now;
        this.completedByUserId = byUser;
        this.updatedAt = now;
    }

    public void cancel(Long byUser, OffsetDateTime now) {
        if (this.state != WorkQueueState.OPEN) return;
        this.state = WorkQueueState.CANCELLED;
        this.completedAt = now;
        this.completedByUserId = byUser;
        this.updatedAt = now;
    }

    // Getters
    public Long getId() { return id; }
    public WorkQueueKind getKind() { return kind; }
    public WorkQueueState getState() { return state; }
    public Long getAssigneeUserId() { return assigneeUserId; }
    public Long getDelegatedFromUserId() { return delegatedFromUserId; }
    public String getSourceType() { return sourceType; }
    public Long getSourceId() { return sourceId; }
    public Long getRelatedDocumentId() { return relatedDocumentId; }
    public Long getRelatedVersionId() { return relatedVersionId; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getLinkPath() { return linkPath; }
    public String getPriority() { return priority; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public Long getCompletedByUserId() { return completedByUserId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // Setters
    public void setKind(WorkQueueKind kind) { this.kind = kind; }
    public void setState(WorkQueueState state) { this.state = state; }
    public void setAssigneeUserId(Long assigneeUserId) { this.assigneeUserId = assigneeUserId; }
    public void setDelegatedFromUserId(Long delegatedFromUserId) { this.delegatedFromUserId = delegatedFromUserId; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public void setRelatedDocumentId(Long relatedDocumentId) { this.relatedDocumentId = relatedDocumentId; }
    public void setRelatedVersionId(Long relatedVersionId) { this.relatedVersionId = relatedVersionId; }
    public void setTitle(String title) { this.title = title; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setLinkPath(String linkPath) { this.linkPath = linkPath; }
    public void setPriority(String priority) { this.priority = priority; }
    public void setDueAt(OffsetDateTime dueAt) { this.dueAt = dueAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
