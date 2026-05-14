package com.lab.edms.notification.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.notification.Notification;
import com.lab.edms.notification.NotificationOutbox;
import com.lab.edms.notification.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * IN_APP 채널: 아웃박스 행을 notifications 테이블에 직접 저장한다.
 */
@Component
public class InAppChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(InAppChannel.class);

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public InAppChannel(NotificationRepository notificationRepository, ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "IN_APP";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public DeliveryResult deliver(NotificationOutbox row) {
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    row.getPayloadJson(),
                    new TypeReference<Map<String, Object>>() {}
            );

            Notification notification = new Notification();
            notification.setRecipientId(row.getRecipientId());
            notification.setEventCode(row.getEventCode());
            notification.setTitle(getString(payload, "title", "알림"));
            notification.setBody(getString(payload, "body", null));
            notification.setLinkPath(getString(payload, "linkPath", null));
            notification.setSeverity(getString(payload, "severity", "INFO"));

            Object docId = payload.get("documentId");
            if (docId != null) {
                notification.setRelatedDocumentId(toLong(docId));
            }

            Object versionId = payload.get("documentVersionId");
            if (versionId != null) {
                notification.setRelatedVersionId(toLong(versionId));
            }

            notification.setRead(false);
            // createdAt은 @PrePersist 에서 자동 설정되지만 명시적으로도 지정
            notification.setCreatedAt(OffsetDateTime.now());

            notificationRepository.save(notification);
            log.debug("IN_APP notification saved: recipientId={}, eventCode={}",
                    row.getRecipientId(), row.getEventCode());
            return DeliveryResult.ok();

        } catch (Exception e) {
            log.error("IN_APP delivery failed: outboxId={}, error={}", row.getId(), e.getMessage(), e);
            return DeliveryResult.fail(e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        String str = value.toString().trim();
        return str.isEmpty() ? defaultValue : str;
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
