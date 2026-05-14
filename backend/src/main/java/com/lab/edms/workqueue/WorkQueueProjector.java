package com.lab.edms.workqueue;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.delegation.Delegation;
import com.lab.edms.delegation.DelegationService;
import com.lab.edms.document.DocumentVersion;
import com.lab.edms.document.DocumentVersionRepository;
import com.lab.edms.user.UserRepository;
import com.lab.edms.workflow.event.EffectiveTransitionedEvent;
import com.lab.edms.workflow.event.WorkflowRejectedEvent;
import com.lab.edms.workflow.event.WorkflowSignedEvent;
import com.lab.edms.workflow.event.WorkflowSubmittedEvent;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 워크플로 이벤트 → work_queue 행 upsert.
 * AFTER_COMMIT으로 바인딩: 원 트랜잭션 커밋 후 독립 트랜잭션에서 실행.
 * uq_wq_source UNIQUE 제약으로 중복 이벤트 멱등 보장.
 */
@Component
public class WorkQueueProjector {

    private static final String SOURCE_TYPE_WF = "WORKFLOW_STEP_INSTANCE";
    private static final String SOURCE_TYPE_WF_INSTANCE = "WORKFLOW_INSTANCE";

    private final WorkQueueRepository repo;
    private final DocumentVersionRepository documentVersionRepo;
    private final UserRepository userRepo;
    private final AuditService auditService;
    private final DelegationService delegationService;

    public WorkQueueProjector(WorkQueueRepository repo,
                              DocumentVersionRepository documentVersionRepo,
                              UserRepository userRepo,
                              AuditService auditService,
                              DelegationService delegationService) {
        this.repo = repo;
        this.documentVersionRepo = documentVersionRepo;
        this.userRepo = userRepo;
        this.auditService = auditService;
        this.delegationService = delegationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmitted(WorkflowSubmittedEvent e) {
        List<Long> reviewerIds = e.reviewerUserIds();
        if (reviewerIds == null || reviewerIds.isEmpty()) return;

        String title = buildTitle(e.documentVersionId());
        for (Long reviewerId : reviewerIds) {
            WorkQueueItem item = buildItem(
                    WorkQueueKind.APPROVAL, reviewerId, null,
                    SOURCE_TYPE_WF_INSTANCE, e.workflowInstanceId(),
                    e.documentId(), e.documentVersionId(), title);
            trySave(item, e.submittedByUserId());

            // 활성 위임 확인 → 위임자(delegate)에게도 항목 생성
            List<Delegation> activeDelegations =
                    delegationService.findActiveDelegatesFor(reviewerId, e.occurredAt());
            for (Delegation delegation : activeDelegations) {
                WorkQueueItem delegatedItem = buildItem(
                        WorkQueueKind.APPROVAL, delegation.getDelegateUserId(), reviewerId,
                        SOURCE_TYPE_WF_INSTANCE, e.workflowInstanceId(),
                        e.documentId(), e.documentVersionId(), title);
                trySave(delegatedItem, "SYSTEM");
                auditService.log(AuditEvent.of("SYSTEM", AuditAction.DELEGATION_USED)
                        .entity("delegations", String.valueOf(delegation.getId()))
                        .build());
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSigned(WorkflowSignedEvent e) {
        // 서명자의 OPEN 항목 DONE 처리
        Long signerDbId = userRepo.findByUserId(e.signerUserId())
                .map(u -> u.getId())
                .orElse(null);

        if (signerDbId != null) {
            repo.findBySourceTypeAndSourceIdAndState(
                    SOURCE_TYPE_WF_INSTANCE, e.workflowInstanceId(), WorkQueueState.OPEN)
                    .stream()
                    .filter(i -> signerDbId.equals(i.getAssigneeUserId()))
                    .forEach(i -> {
                        i.markDone(signerDbId, e.occurredAt());
                        repo.save(i);
                        auditService.log(AuditEvent.of(e.signerUserId(),
                                AuditAction.WORK_QUEUE_ITEM_DONE)
                                .entity("work_queue", String.valueOf(i.getId()))
                                .build());
                    });
        }

        // step 완료 → 다음 assignee에게 새 OPEN 항목 생성
        if (e.stepCompleted() && !e.nextStepAssigneeIds().isEmpty()) {
            String title = buildTitle(e.documentVersionId());
            for (Long nextId : e.nextStepAssigneeIds()) {
                WorkQueueItem next = buildItem(
                        WorkQueueKind.APPROVAL, nextId, null,
                        SOURCE_TYPE_WF_INSTANCE, e.workflowInstanceId(),
                        e.documentId(), e.documentVersionId(), title);
                trySave(next, "SYSTEM");
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRejected(WorkflowRejectedEvent e) {
        List<WorkQueueItem> openItems = repo.findByRelatedVersionIdAndState(
                e.documentVersionId(), WorkQueueState.OPEN);
        for (WorkQueueItem item : openItems) {
            item.cancel(null, e.occurredAt());
            repo.save(item);
            auditService.log(AuditEvent.of(e.rejectedByUserId(),
                    AuditAction.WORK_QUEUE_ITEM_CANCELLED)
                    .entity("work_queue", String.valueOf(item.getId()))
                    .build());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEffective(EffectiveTransitionedEvent e) {
        // work_queue 영향 없음 — Read&Ack는 M9에서
    }

    private WorkQueueItem buildItem(WorkQueueKind kind, Long assigneeId, Long delegatedFromId,
                                     String sourceType, Long sourceId,
                                     Long docId, Long verId, String title) {
        WorkQueueItem item = new WorkQueueItem();
        item.setKind(kind);
        item.setAssigneeUserId(assigneeId);
        item.setDelegatedFromUserId(delegatedFromId);
        item.setSourceType(sourceType);
        item.setSourceId(sourceId);
        item.setRelatedDocumentId(docId);
        item.setRelatedVersionId(verId);
        item.setTitle(title);
        item.setLinkPath("/documents/" + docId + "/versions/" + verId);
        item.setPriority("NORMAL");
        return item;
    }

    private String buildTitle(Long verId) {
        return documentVersionRepo.findById(verId)
                .map(DocumentVersion::getTitle)
                .map(t -> "결재 요청: " + t)
                .orElse("결재 요청");
    }

    private void trySave(WorkQueueItem item, String actor) {
        try {
            repo.save(item);
            auditService.log(AuditEvent.of(actor, AuditAction.WORK_QUEUE_ITEM_OPENED)
                    .entity("work_queue", String.valueOf(item.getId()))
                    .build());
        } catch (DataIntegrityViolationException ex) {
            // uq_wq_source 중복 — 멱등 처리
        }
    }
}
