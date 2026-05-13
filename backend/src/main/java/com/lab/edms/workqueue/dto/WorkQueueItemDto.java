package com.lab.edms.workqueue.dto;

import com.lab.edms.workqueue.WorkQueueItem;
import com.lab.edms.workqueue.WorkQueueKind;
import com.lab.edms.workqueue.WorkQueueState;

import java.time.OffsetDateTime;

public record WorkQueueItemDto(
        Long id,
        WorkQueueKind kind,
        WorkQueueState state,
        Long assigneeUserId,
        Long delegatedFromUserId,
        String sourceType,
        Long sourceId,
        Long relatedDocumentId,
        Long relatedVersionId,
        String title,
        String summary,
        String linkPath,
        String priority,
        OffsetDateTime dueAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt
) {
    public static WorkQueueItemDto from(WorkQueueItem item) {
        return new WorkQueueItemDto(
                item.getId(),
                item.getKind(),
                item.getState(),
                item.getAssigneeUserId(),
                item.getDelegatedFromUserId(),
                item.getSourceType(),
                item.getSourceId(),
                item.getRelatedDocumentId(),
                item.getRelatedVersionId(),
                item.getTitle(),
                item.getSummary(),
                item.getLinkPath(),
                item.getPriority(),
                item.getDueAt(),
                item.getCompletedAt(),
                item.getCreatedAt()
        );
    }
}
