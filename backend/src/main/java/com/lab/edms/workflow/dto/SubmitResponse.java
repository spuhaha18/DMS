package com.lab.edms.workflow.dto;

import com.lab.edms.workflow.AssigneeRef;
import java.util.List;

public record SubmitResponse(
        Long workflowId,
        int currentStep,
        List<AssigneeRef> firstStepAssignees
) {}
