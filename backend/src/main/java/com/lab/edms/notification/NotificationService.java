package com.lab.edms.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.common.ForbiddenException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationEventCodeRepository eventCodeRepository;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationOutboxRepository outboxRepository,
                               NotificationEventCodeRepository eventCodeRepository,
                               ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.outboxRepository = outboxRepository;
        this.eventCodeRepository = eventCodeRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 주어진 수신자/이벤트코드/채널 목록으로 outbox 행을 직접 삽입한다.
     * 다른 컴포넌트에서 프로그래매틱하게 알림을 큐잉할 때 사용.
     */
    public void queue(Long recipientId, String eventCode, Map<String, Object> payload, List<String> channels) {
        String payloadJson = toJson(payload);
        OffsetDateTime now = OffsetDateTime.now();
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

    /**
     * 알림을 읽음 처리한다. 소유권을 검증하여 본인의 알림만 읽음 처리 가능.
     *
     * @throws ForbiddenException 알림 수신자가 currentUserId와 다를 경우
     */
    @Transactional
    public void markRead(Long notificationId, Long currentUserId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Notification not found: " + notificationId));

        if (!notification.getRecipientId().equals(currentUserId)) {
            throw new ForbiddenException("알림에 대한 접근 권한이 없습니다.");
        }

        if (!notification.isRead()) {
            notification.markRead(OffsetDateTime.now());
            notificationRepository.save(notification);
        }
    }

    /**
     * 사용자의 알림 목록을 최신순으로 페이지 조회한다.
     */
    @Transactional(readOnly = true)
    public Page<Notification> listForUser(Long userId, Pageable pageable) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * 사용자의 읽지 않은 알림 수를 반환한다.
     */
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    // -----------------------------------------------------------------------
    // private helpers
    // -----------------------------------------------------------------------

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("payload JSON 직렬화 실패", e);
        }
    }
}
