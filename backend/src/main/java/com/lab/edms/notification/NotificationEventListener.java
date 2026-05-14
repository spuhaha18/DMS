package com.lab.edms.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.user.UserRepository;
import com.lab.edms.workflow.event.EffectiveTransitionedEvent;
import com.lab.edms.workflow.event.WorkflowRejectedEvent;
import com.lab.edms.workflow.event.WorkflowSignedEvent;
import com.lab.edms.workflow.event.WorkflowSubmittedEvent;
import com.lab.edms.document.DocumentVersionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 워크플로 도메인 이벤트 → notification_outbox 행 삽입.
 * AFTER_COMMIT + REQUIRES_NEW 패턴으로 원 트랜잭션과 독립 실행.
 *
 * 중요: notifications 테이블에는 직접 삽입하지 않는다.
 * 실제 알림 생성은 InAppChannel 디스패처가 outbox를 처리할 때 수행한다.
 */
@Component
public class NotificationEventListener {

    private static final String EVT_SUBMITTED = "WORKFLOW_SUBMITTED";
    private static final String EVT_SIGNED    = "WORKFLOW_SIGNED";
    private static final String EVT_REJECTED  = "WORKFLOW_REJECTED";
    private static final String EVT_EFFECTIVE = "DOCUMENT_EFFECTIVE";

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationEventCodeRepository eventCodeRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(NotificationOutboxRepository outboxRepository,
                                     NotificationEventCodeRepository eventCodeRepository,
                                     DocumentVersionRepository documentVersionRepository,
                                     UserRepository userRepository,
                                     AuditService auditService,
                                     ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.eventCodeRepository = eventCodeRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    // -----------------------------------------------------------------------
    // WORKFLOW_SUBMITTED — 리뷰어 전원에게 IN_APP + EMAIL_SMTP
    // -----------------------------------------------------------------------
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmitted(WorkflowSubmittedEvent e) {
        String[] channels = resolveChannels(EVT_SUBMITTED);
        Map<String, Object> payloadBase = new HashMap<>();
        payloadBase.put("eventCode", EVT_SUBMITTED);
        payloadBase.put("documentVersionId", e.documentVersionId());
        payloadBase.put("documentId", e.documentId());
        payloadBase.put("workflowInstanceId", e.workflowInstanceId());

        OffsetDateTime now = OffsetDateTime.now();
        List<Long> reviewerIds = e.reviewerUserIds();
        if (reviewerIds == null || reviewerIds.isEmpty()) return;
        for (Long reviewerId : reviewerIds) {
            queueOutbox(reviewerId, EVT_SUBMITTED, channels, payloadBase, now);
            auditService.log(AuditEvent.of(e.submittedByUserId(), AuditAction.NOTIFICATION_QUEUED)
                    .entity("notification_outbox", "recipient=" + reviewerId)
                    .build());
        }
    }

    // -----------------------------------------------------------------------
    // WORKFLOW_SIGNED — 서명자에게 IN_APP
    // -----------------------------------------------------------------------
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSigned(WorkflowSignedEvent e) {
        Long signerDbId = userRepository.findByUserId(e.signerUserId())
                .map(u -> u.getId())
                .orElse(null);
        if (signerDbId == null) return;

        String[] channels = resolveChannels(EVT_SIGNED);
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventCode", EVT_SIGNED);
        payload.put("documentVersionId", e.documentVersionId());
        payload.put("documentId", e.documentId());
        payload.put("workflowInstanceId", e.workflowInstanceId());
        payload.put("stepInstanceId", e.stepInstanceId());

        OffsetDateTime now = OffsetDateTime.now();
        queueOutbox(signerDbId, EVT_SIGNED, channels, payload, now);
        auditService.log(AuditEvent.of(e.signerUserId(), AuditAction.NOTIFICATION_QUEUED)
                .entity("notification_outbox", "recipient=" + signerDbId)
                .build());
    }

    // -----------------------------------------------------------------------
    // WORKFLOW_REJECTED — 문서 소유자(createdBy)에게 IN_APP + EMAIL_SMTP
    // -----------------------------------------------------------------------
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRejected(WorkflowRejectedEvent e) {
        Long ownerUserId = documentVersionRepository.findById(e.documentVersionId())
                .map(v -> v.getCreatedBy())
                .orElse(null);
        if (ownerUserId == null) return;

        String[] channels = resolveChannels(EVT_REJECTED);
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventCode", EVT_REJECTED);
        payload.put("documentVersionId", e.documentVersionId());
        payload.put("documentId", e.documentId());
        payload.put("workflowInstanceId", e.workflowInstanceId());
        payload.put("reason", e.reason());

        OffsetDateTime now = OffsetDateTime.now();
        queueOutbox(ownerUserId, EVT_REJECTED, channels, payload, now);
        auditService.log(AuditEvent.of(e.rejectedByUserId(), AuditAction.NOTIFICATION_QUEUED)
                .entity("notification_outbox", "recipient=" + ownerUserId)
                .build());
    }

    // -----------------------------------------------------------------------
    // DOCUMENT_EFFECTIVE — 문서 소유자에게 IN_APP + EMAIL_SMTP
    // -----------------------------------------------------------------------
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEffective(EffectiveTransitionedEvent e) {
        Long ownerUserId = e.ownerUserId();
        if (ownerUserId == null) return;

        String[] channels = resolveChannels(EVT_EFFECTIVE);
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventCode", EVT_EFFECTIVE);
        payload.put("documentVersionId", e.documentVersionId());
        payload.put("documentId", e.documentId());
        payload.put("workflowInstanceId", e.workflowInstanceId());
        payload.put("effectiveDate", e.effectiveDate() != null ? e.effectiveDate().toString() : null);

        OffsetDateTime now = OffsetDateTime.now();
        queueOutbox(ownerUserId, EVT_EFFECTIVE, channels, payload, now);
        auditService.log(AuditEvent.of("SYSTEM", AuditAction.NOTIFICATION_QUEUED)
                .entity("notification_outbox", "recipient=" + ownerUserId)
                .build());
    }

    // -----------------------------------------------------------------------
    // private helpers
    // -----------------------------------------------------------------------

    /**
     * 이벤트 코드에 해당하는 채널 목록을 DB에서 조회한다.
     * DB에 없으면 IN_APP 단일 채널로 폴백.
     */
    private String[] resolveChannels(String eventCode) {
        return eventCodeRepository.findById(eventCode)
                .map(NotificationEventCode::getDefaultChannels)
                .orElse(new String[]{"IN_APP"});
    }

    /**
     * recipientId × channels 수만큼 outbox 행을 삽입한다.
     */
    private void queueOutbox(Long recipientId, String eventCode, String[] channels,
                              Map<String, Object> payload, OffsetDateTime now) {
        String payloadJson = toJson(payload);
        for (String channel : channels) {
            NotificationOutbox outbox = new NotificationOutbox();
            outbox.setRecipientId(recipientId);
            outbox.setChannel(channel);
            outbox.setEventCode(eventCode);
            outbox.setPayloadJson(payloadJson);
            outbox.setStatus("PENDING");
            outbox.setNextAttemptAt(now);
            outboxRepository.save(outbox);
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("payload JSON 직렬화 실패", ex);
        }
    }
}
