package com.lab.edms.delegation;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "delegations")
public class Delegation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delegator_user_id", nullable = false)
    private Long delegatorUserId;

    @Column(name = "delegate_user_id", nullable = false)
    private Long delegateUserId;

    @Column(name = "scope_kind", nullable = false, length = 30)
    private String scopeKind;

    @Column(name = "scope_value", length = 100)
    private String scopeValue;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "valid_from", nullable = false)
    private OffsetDateTime validFrom;

    @Column(name = "valid_to", nullable = false)
    private OffsetDateTime validTo;

    @Column(nullable = false, length = 20)
    private String state = "REQUESTED";

    @Column(name = "qa_approver_user_id")
    private Long qaApproverUserId;

    @Column(name = "qa_approved_at")
    private OffsetDateTime qaApprovedAt;

    @Column(name = "qa_rejection_reason", columnDefinition = "TEXT")
    private String qaRejectionReason;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by_user_id")
    private Long revokedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public boolean isActive(OffsetDateTime at) {
        return "APPROVED".equals(state) && !validFrom.isAfter(at) && validTo.isAfter(at);
    }

    // Getters
    public Long getId() { return id; }
    public Long getDelegatorUserId() { return delegatorUserId; }
    public Long getDelegateUserId() { return delegateUserId; }
    public String getScopeKind() { return scopeKind; }
    public String getScopeValue() { return scopeValue; }
    public String getReason() { return reason; }
    public OffsetDateTime getValidFrom() { return validFrom; }
    public OffsetDateTime getValidTo() { return validTo; }
    public String getState() { return state; }
    public Long getQaApproverUserId() { return qaApproverUserId; }
    public OffsetDateTime getQaApprovedAt() { return qaApprovedAt; }
    public String getQaRejectionReason() { return qaRejectionReason; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public Long getRevokedByUserId() { return revokedByUserId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    // Setters
    public void setDelegatorUserId(Long delegatorUserId) { this.delegatorUserId = delegatorUserId; }
    public void setDelegateUserId(Long delegateUserId) { this.delegateUserId = delegateUserId; }
    public void setScopeKind(String scopeKind) { this.scopeKind = scopeKind; }
    public void setScopeValue(String scopeValue) { this.scopeValue = scopeValue; }
    public void setReason(String reason) { this.reason = reason; }
    public void setValidFrom(OffsetDateTime validFrom) { this.validFrom = validFrom; }
    public void setValidTo(OffsetDateTime validTo) { this.validTo = validTo; }
    public void setState(String state) { this.state = state; }
    public void setQaApproverUserId(Long qaApproverUserId) { this.qaApproverUserId = qaApproverUserId; }
    public void setQaApprovedAt(OffsetDateTime qaApprovedAt) { this.qaApprovedAt = qaApprovedAt; }
    public void setQaRejectionReason(String qaRejectionReason) { this.qaRejectionReason = qaRejectionReason; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
    public void setRevokedByUserId(Long revokedByUserId) { this.revokedByUserId = revokedByUserId; }
}
