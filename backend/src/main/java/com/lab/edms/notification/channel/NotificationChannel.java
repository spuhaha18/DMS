package com.lab.edms.notification.channel;

import com.lab.edms.notification.NotificationOutbox;

public interface NotificationChannel {

    /** 채널 식별자: "IN_APP", "EMAIL_SMTP", "EMAIL_LOG" */
    String name();

    /** 아웃박스 행 하나를 실제로 전달한다. */
    DeliveryResult deliver(NotificationOutbox row);

    /** 이 채널이 현재 활성화되어 있는지 여부 */
    boolean enabled();
}
