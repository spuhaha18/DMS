package com.lab.edms.delegation.dto;

import com.lab.edms.delegation.Delegation;

public record DelegationDto(
        Long id,
        Long delegatorUserId,
        Long delegateUserId,
        String scopeKind,
        String scopeValue,
        String reason,
        String validFrom,
        String validTo,
        String state,
        Long qaApproverUserId,
        String qaApprovedAt,
        String qaRejectionReason,
        String revokedAt,
        Long revokedByUserId,
        String createdAt
) {
    public static DelegationDto from(Delegation d) {
        return new DelegationDto(
                d.getId(),
                d.getDelegatorUserId(),
                d.getDelegateUserId(),
                d.getScopeKind(),
                d.getScopeValue(),
                d.getReason(),
                d.getValidFrom() != null ? d.getValidFrom().toString() : null,
                d.getValidTo() != null ? d.getValidTo().toString() : null,
                d.getState(),
                d.getQaApproverUserId(),
                d.getQaApprovedAt() != null ? d.getQaApprovedAt().toString() : null,
                d.getQaRejectionReason(),
                d.getRevokedAt() != null ? d.getRevokedAt().toString() : null,
                d.getRevokedByUserId(),
                d.getCreatedAt() != null ? d.getCreatedAt().toString() : null
        );
    }
}
