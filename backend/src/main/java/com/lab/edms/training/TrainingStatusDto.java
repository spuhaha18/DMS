package com.lab.edms.training;

import java.time.OffsetDateTime;

public record TrainingStatusDto(
        Long assignmentId,
        Long userId,
        String userDisplayName,
        OffsetDateTime assignedAt,
        OffsetDateTime completedAt,
        boolean completed,
        double completionRate
) {}
