package com.lab.edms.workflow.dto;

import java.util.List;
import java.util.Map;

public record SubmitRequest(
        String reasonForChange,
        Map<Integer, List<String>> manualAssignees  // stepOrder → userIdString 목록
) {}
