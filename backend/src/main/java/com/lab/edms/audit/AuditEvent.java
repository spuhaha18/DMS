package com.lab.edms.audit;

import java.time.OffsetDateTime;

public record AuditEvent(
        String actorUserId,
        AuditAction action,
        String entityType,
        String entityId,
        String beforeValue,
        String afterValue,
        String reason,
        String clientIp,
        OffsetDateTime serverTs
) {
    public AuditEvent {
        if (action == null) throw new IllegalArgumentException("action required");
        if (entityType == null) throw new IllegalArgumentException("entityType required");
        if (serverTs == null) serverTs = OffsetDateTime.now();
    }
}
