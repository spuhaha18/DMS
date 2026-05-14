package com.lab.edms.notification.dto;

import com.lab.edms.notification.Notification;

public record NotificationDto(
        Long id,
        String eventCode,
        String title,
        String body,
        boolean read,
        String readAt,
        String linkPath,
        Long relatedDocumentId,
        Long relatedVersionId,
        String severity,
        String createdAt
) {
    public static NotificationDto from(Notification n) {
        return new NotificationDto(
                n.getId(),
                n.getEventCode(),
                n.getTitle(),
                n.getBody(),
                n.isRead(),
                n.getReadAt() != null ? n.getReadAt().toString() : null,
                n.getLinkPath(),
                n.getRelatedDocumentId(),
                n.getRelatedVersionId(),
                n.getSeverity(),
                n.getCreatedAt() != null ? n.getCreatedAt().toString() : null
        );
    }
}
