package com.lab.edms.delegation.dto;

public record DelegationRequestBody(
        Long delegateUserId,
        String scopeKind,
        String scopeValue,
        String reason,
        String validFrom,
        String validTo
) {}
