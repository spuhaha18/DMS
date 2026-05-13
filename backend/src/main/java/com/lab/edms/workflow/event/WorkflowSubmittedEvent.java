package com.lab.edms.workflow.event;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkflowSubmittedEvent(
        Long workflowInstanceId,
        Long documentVersionId,
        Long documentId,
        String submittedByUserId,
        List<Long> reviewerUserIds,
        OffsetDateTime occurredAt
) {}
