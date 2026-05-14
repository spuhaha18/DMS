package com.lab.edms.notification.channel;

import com.lab.edms.notification.NotificationOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * EMAIL_LOG 채널: 실제 메일 발송 없이 로그로만 출력한다.
 * application.yml 에서 notification.email.log.enabled=true 로 활성화.
 */
@Component
@ConditionalOnProperty(name = "notification.email.log.enabled", havingValue = "true", matchIfMissing = false)
public class LogEmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(LogEmailChannel.class);

    @Value("${notification.email.log.enabled:false}")
    private boolean logEnabled;

    @Override
    public String name() {
        return "EMAIL_LOG";
    }

    @Override
    public boolean enabled() {
        return logEnabled;
    }

    @Override
    public DeliveryResult deliver(NotificationOutbox row) {
        log.info("EMAIL[LOG]: recipient={}, eventCode={}, payload={}",
                row.getRecipientId(), row.getEventCode(), row.getPayloadJson());
        return DeliveryResult.ok();
    }
}
