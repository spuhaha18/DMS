package com.lab.edms.workflow.event;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkflowSignedEvent(
        Long workflowInstanceId,
        Long stepInstanceId,
        Long documentVersionId,
        Long documentId,
        String signerUserId,
        boolean stepCompleted,
        List<Long> nextStepAssigneeIds,
        OffsetDateTime occurredAt
) {}
