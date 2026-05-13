package com.lab.edms.workflow.event;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record EffectiveTransitionedEvent(
        Long workflowInstanceId,
        Long documentVersionId,
        Long documentId,
        Long ownerUserId,
        LocalDate effectiveDate,
        OffsetDateTime occurredAt
) {}
