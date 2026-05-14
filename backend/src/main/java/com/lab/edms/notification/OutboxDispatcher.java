package com.lab.edms.notification;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.notification.channel.DeliveryResult;
import com.lab.edms.notification.channel.NotificationChannel;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final NotificationOutboxRepository outboxRepo;
    private final NotificationDeadLetterRepository dlqRepo;
    private final Map<String, NotificationChannel> channelsByName;
    private final AuditService auditService;
    private final Clock clock;

    @Value("${notification.outbox.max-retries:3}")
    private int maxRetries;

    public OutboxDispatcher(NotificationOutboxRepository outboxRepo,
                            NotificationDeadLetterRepository dlqRepo,
                            List<NotificationChannel> channels,
                            AuditService auditService,
                            Clock clock) {
        this.outboxRepo = outboxRepo;
        this.dlqRepo = dlqRepo;
        this.channelsByName = channels.stream()
                .collect(Collectors.toMap(NotificationChannel::name, Function.identity()));
        this.auditService = auditService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${notification.outbox.dispatch-interval-ms:10000}")
    @SchedulerLock(name = "NotificationOutboxDispatcher", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
    @Transactional
    public void dispatch() {
        List<NotificationOutbox> due = outboxRepo.findDueForDispatch(OffsetDateTime.now(clock));
        for (NotificationOutbox row : due) {
            dispatchOne(row);
        }
    }

    private void dispatchOne(NotificationOutbox row) {
        // Status guard — idempotency
        if ("DELIVERED".equals(row.getStatus()) || "DEAD".equals(row.getStatus())) {
            return;
        }

        NotificationChannel channel = channelsByName.get(row.getChannel());
        if (channel == null || !channel.enabled()) {
            // Channel not available — move to DLQ immediately
            moveToDlq(row, "Channel not available: " + row.getChannel());
            return;
        }

        row.setStatus("SENDING");
        outboxRepo.save(row);

        DeliveryResult result;
        try {
            result = channel.deliver(row);
        } catch (Exception e) {
            result = DeliveryResult.fail(e.getMessage());
        }

        if (result.success()) {
            row.setStatus("DELIVERED");
            row.setDeliveredAt(OffsetDateTime.now(clock));
            row.setErrorMessage(null);
            outboxRepo.save(row);
            auditService.log(AuditEvent.of("SYSTEM", AuditAction.NOTIFICATION_DELIVERED)
                    .entity("notification_outbox", String.valueOf(row.getId()))
                    .build());
        } else {
            row.setAttemptCount(row.getAttemptCount() + 1);
            row.setErrorMessage(result.errorMessage() != null
                    ? result.errorMessage().substring(0, Math.min(result.errorMessage().length(), 999))
                    : "Unknown error");

            if (row.getAttemptCount() >= maxRetries) {
                moveToDlq(row, row.getErrorMessage());
            } else {
                row.setStatus("FAILED");
                row.setNextAttemptAt(OffsetDateTime.now(clock).plus(backoffFor(row.getAttemptCount())));
                outboxRepo.save(row);
                auditService.log(AuditEvent.of("SYSTEM", AuditAction.NOTIFICATION_DELIVERY_FAILED)
                        .entity("notification_outbox", String.valueOf(row.getId()))
                        .build());
            }
        }
    }

    private void moveToDlq(NotificationOutbox row, String reason) {
        row.setStatus("DEAD");
        outboxRepo.save(row);

        NotificationDeadLetter dlq = new NotificationDeadLetter();
        dlq.setOutboxId(row.getId());
        dlq.setRecipientId(row.getRecipientId());
        dlq.setChannel(row.getChannel());
        dlq.setEventCode(row.getEventCode());
        dlq.setPayloadJson(row.getPayloadJson());
        dlq.setErrorHistory("[\"" + (reason != null ? reason.replace("\"", "'") : "unknown") + "\"]");
        dlq.setMovedAt(OffsetDateTime.now(clock));
        dlqRepo.save(dlq);

        auditService.log(AuditEvent.of("SYSTEM", AuditAction.NOTIFICATION_DELIVERY_FAILED)
                .entity("notification_dlq", "outbox=" + row.getId())
                .build());
    }

    private Duration backoffFor(int attempt) {
        // Exponential backoff: 1min, 5min, 15min
        return switch (attempt) {
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(5);
            default -> Duration.ofMinutes(15);
        };
    }
}
