package com.lab.edms.workflow.dto;

import java.util.List;

public record WorkflowInstanceDto(
        Long workflowId,
        String state,
        int currentStep,
        List<WorkflowStepInstanceDto> steps
) {}
