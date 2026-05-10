package com.lab.edms.workflow.dto;

import java.time.Instant;

public record PendingTaskDto(
        Long docId,
        String docNumber,
        String title,
        Long verId,
        String state,
        Long workflowId,
        Long stepInstanceId,
        int stepOrder,
        String stepType,
        String roleCode,
        Instant assignedAt
) {}
