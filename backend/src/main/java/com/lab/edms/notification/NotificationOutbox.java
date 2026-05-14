package com.lab.edms.notification;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "channel", nullable = false, length = 30)
    private String channel;

    @Column(name = "event_code", nullable = false, length = 60)
    private String eventCode;

    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (nextAttemptAt == null) nextAttemptAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters
    public Long getId() { return id; }
    public Long getRecipientId() { return recipientId; }
    public String getChannel() { return channel; }
    public String getEventCode() { return eventCode; }
    public String getPayloadJson() { return payloadJson; }
    public String getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public String getErrorMessage() { return errorMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // Setters
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    public void setChannel(String channel) { this.channel = channel; }
    public void setEventCode(String eventCode) { this.eventCode = eventCode; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public void setStatus(String status) { this.status = status; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public void setNextAttemptAt(OffsetDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public void setDeliveredAt(OffsetDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
