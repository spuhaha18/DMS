package com.lab.edms.audit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
        if (serverTs == null) serverTs = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /** Start building an AuditEvent with the two required fields. */
    public static Builder of(String actorUserId, AuditAction action) {
        return new Builder(actorUserId, action);
    }

    public static final class Builder {
        private final String actorUserId;
        private final AuditAction action;
        private String entityType;
        private String entityId;
        private String beforeValue;
        private String afterValue;
        private String reason;
        private String clientIp;
        private OffsetDateTime serverTs;

        private Builder(String actorUserId, AuditAction action) {
            this.actorUserId = actorUserId;
            this.action = action;
        }

        public Builder entity(String type, String id) {
            this.entityType = type;
            this.entityId = id;
            return this;
        }

        public Builder before(String json) {
            this.beforeValue = json;
            return this;
        }

        public Builder after(String json) {
            this.afterValue = json;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder ip(String clientIp) {
            this.clientIp = clientIp;
            return this;
        }

        /** Set server timestamp. Primarily for testing purposes; normally auto-populated. */
        public Builder serverTs(OffsetDateTime ts) {
            this.serverTs = ts;
            return this;
        }

        public AuditEvent build() {
            OffsetDateTime ts = (serverTs != null) ? serverTs : OffsetDateTime.now(ZoneOffset.UTC);
            return new AuditEvent(actorUserId, action, entityType, entityId,
                    beforeValue, afterValue, reason, clientIp, ts);
        }
    }
}
