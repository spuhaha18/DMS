package com.lab.edms.workqueue;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkQueueRepository extends JpaRepository<WorkQueueItem, Long> {

    Optional<WorkQueueItem> findByKindAndSourceTypeAndSourceIdAndAssigneeUserId(
            WorkQueueKind kind, String sourceType, Long sourceId, Long assigneeUserId);

    Page<WorkQueueItem> findByAssigneeUserIdAndStateOrderByCreatedAtDesc(
            Long assigneeUserId, WorkQueueState state, Pageable pageable);

    Page<WorkQueueItem> findByAssigneeUserIdAndKindAndStateOrderByCreatedAtDesc(
            Long assigneeUserId, WorkQueueKind kind, WorkQueueState state, Pageable pageable);

    Page<WorkQueueItem> findByAssigneeUserIdOrderByCreatedAtDesc(
            Long assigneeUserId, Pageable pageable);

    long countByAssigneeUserIdAndState(Long assigneeUserId, WorkQueueState state);

    java.util.List<WorkQueueItem> findByRelatedVersionIdAndState(Long relatedVersionId, WorkQueueState state);

    java.util.List<WorkQueueItem> findBySourceTypeAndSourceIdAndState(
            String sourceType, Long sourceId, WorkQueueState state);
}
