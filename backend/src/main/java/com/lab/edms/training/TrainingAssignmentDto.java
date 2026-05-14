package com.lab.edms.training;

import java.time.OffsetDateTime;

public record TrainingAssignmentDto(
        Long id,
        Long versionId,
        String docNumber,
        String docTitle,
        OffsetDateTime assignedAt,
        OffsetDateTime dueAt,
        OffsetDateTime completedAt,
        boolean completed
) {
    public static TrainingAssignmentDto from(TrainingAssignment a, String docNumber, String docTitle) {
        return new TrainingAssignmentDto(
                a.getId(),
                a.getVersionId(),
                docNumber,
                docTitle,
                a.getAssignedAt(),
                a.getDueAt(),
                a.getCompletedAt(),
                a.isCompleted()
        );
    }
}
