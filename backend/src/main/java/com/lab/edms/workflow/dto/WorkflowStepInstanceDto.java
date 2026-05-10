package com.lab.edms.workflow.dto;

import com.lab.edms.workflow.AssigneeRef;
import com.lab.edms.workflow.SignedRef;

import java.util.List;

public record WorkflowStepInstanceDto(
        Long stepInstanceId,
        int stepOrder,
        String stepType,
        String roleCode,
        int minSigners,
        boolean parallel,
        boolean qaRequired,
        String state,
        List<AssigneeRef> assignees,
        List<SignedRef> signed
) {}
