package com.lab.edms.notification;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_dlq")
public class NotificationDeadLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "outbox_id", nullable = false)
    private Long outboxId;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "channel", nullable = false, length = 30)
    private String channel;

    @Column(name = "event_code", nullable = false, length = 60)
    private String eventCode;

    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    /**
     * JSON 배열 문자열로 저장 (예: [{"attempt":1,"error":"...","ts":"..."},...])
     */
    @Column(name = "error_history", nullable = false, columnDefinition = "jsonb")
    private String errorHistory;

    @Column(name = "moved_at", nullable = false)
    private OffsetDateTime movedAt;

    @Column(name = "acknowledged_by_user_id")
    private Long acknowledgedByUserId;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @PrePersist
    protected void onCreate() {
        if (movedAt == null) movedAt = OffsetDateTime.now();
    }

    // Getters
    public Long getId() { return id; }
    public Long getOutboxId() { return outboxId; }
    public Long getRecipientId() { return recipientId; }
    public String getChannel() { return channel; }
    public String getEventCode() { return eventCode; }
    public String getPayloadJson() { return payloadJson; }
    public String getErrorHistory() { return errorHistory; }
    public OffsetDateTime getMovedAt() { return movedAt; }
    public Long getAcknowledgedByUserId() { return acknowledgedByUserId; }
    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }

    // Setters
    public void setOutboxId(Long outboxId) { this.outboxId = outboxId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    public void setChannel(String channel) { this.channel = channel; }
    public void setEventCode(String eventCode) { this.eventCode = eventCode; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public void setErrorHistory(String errorHistory) { this.errorHistory = errorHistory; }
    public void setMovedAt(OffsetDateTime movedAt) { this.movedAt = movedAt; }
    public void setAcknowledgedByUserId(Long acknowledgedByUserId) { this.acknowledgedByUserId = acknowledgedByUserId; }
    public void setAcknowledgedAt(OffsetDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
}
