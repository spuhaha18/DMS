package com.lab.edms.notification;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notifications_archived")
public class NotificationArchived {

    @Id
    @Column(name = "id", nullable = false)
    private Long id; // NO @GeneratedValue — preserves original notifications.id

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "event_code", nullable = false, length = 60)
    private String eventCode;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "link_path", length = 300)
    private String linkPath;

    @Column(name = "related_document_id")
    private Long relatedDocumentId;

    @Column(name = "related_version_id")
    private Long relatedVersionId;

    @Column(name = "related_work_item_id")
    private Long relatedWorkItemId;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity = "INFO";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "archived_at", nullable = false)
    private OffsetDateTime archivedAt;

    // Getters
    public Long getId() { return id; }
    public Long getRecipientId() { return recipientId; }
    public String getEventCode() { return eventCode; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public boolean isRead() { return isRead; }
    public OffsetDateTime getReadAt() { return readAt; }
    public String getLinkPath() { return linkPath; }
    public Long getRelatedDocumentId() { return relatedDocumentId; }
    public Long getRelatedVersionId() { return relatedVersionId; }
    public Long getRelatedWorkItemId() { return relatedWorkItemId; }
    public String getSeverity() { return severity; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getArchivedAt() { return archivedAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    public void setEventCode(String eventCode) { this.eventCode = eventCode; }
    public void setTitle(String title) { this.title = title; }
    public void setBody(String body) { this.body = body; }
    public void setRead(boolean read) { this.isRead = read; }
    public void setReadAt(OffsetDateTime readAt) { this.readAt = readAt; }
    public void setLinkPath(String linkPath) { this.linkPath = linkPath; }
    public void setRelatedDocumentId(Long relatedDocumentId) { this.relatedDocumentId = relatedDocumentId; }
    public void setRelatedVersionId(Long relatedVersionId) { this.relatedVersionId = relatedVersionId; }
    public void setRelatedWorkItemId(Long relatedWorkItemId) { this.relatedWorkItemId = relatedWorkItemId; }
    public void setSeverity(String severity) { this.severity = severity; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public void setArchivedAt(OffsetDateTime archivedAt) { this.archivedAt = archivedAt; }
}
