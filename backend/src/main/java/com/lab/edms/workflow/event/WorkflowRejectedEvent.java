package com.lab.edms.workflow.event;

import java.time.OffsetDateTime;

public record WorkflowRejectedEvent(
        Long workflowInstanceId,
        Long documentVersionId,
        Long documentId,
        String rejectedByUserId,
        String reason,
        OffsetDateTime occurredAt
) {}
