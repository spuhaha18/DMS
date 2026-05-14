package com.lab.edms.notification;

import com.lab.edms.audit.AuditService;
import com.lab.edms.notification.channel.DeliveryResult;
import com.lab.edms.notification.channel.NotificationChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {

    @Mock
    private NotificationOutboxRepository outboxRepo;

    @Mock
    private NotificationDeadLetterRepository dlqRepo;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationChannel channel;

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    /** 채널 name() stub을 먼저 설정한 후 dispatcher를 생성해야 channelsByName 맵이 올바르게 구성된다. */
    private OutboxDispatcher buildDispatcher() {
        OutboxDispatcher d = new OutboxDispatcher(
                outboxRepo, dlqRepo, List.of(channel), auditService, FIXED_CLOCK);
        try {
            var field = OutboxDispatcher.class.getDeclaredField("maxRetries");
            field.setAccessible(true);
            field.set(d, 3);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return d;
    }

    private NotificationOutbox pendingRow() {
        NotificationOutbox row = new NotificationOutbox();
        row.setStatus("PENDING");
        row.setChannel("IN_APP");
        row.setAttemptCount(0);
        return row;
    }

    // ──────────────────────────────────────────────────────────
    // 1. Happy path: PENDING + 채널 성공 → DELIVERED + audit 호출
    // ──────────────────────────────────────────────────────────
    @Test
    void dispatch_happy_path() {
        when(channel.name()).thenReturn("IN_APP");
        OutboxDispatcher dispatcher = buildDispatcher();

        NotificationOutbox row = pendingRow();
        when(outboxRepo.findDueForDispatch(any(OffsetDateTime.class))).thenReturn(List.of(row));
        when(channel.enabled()).thenReturn(true);
        when(channel.deliver(row)).thenReturn(DeliveryResult.ok());
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dispatcher.dispatch();

        assertThat(row.getStatus()).isEqualTo("DELIVERED");
        assertThat(row.getDeliveredAt()).isNotNull();
        assertThat(row.getErrorMessage()).isNull();
        verify(auditService, times(1)).log(any());
    }

    // ──────────────────────────────────────────────────────────
    // 2. 채널 실패 + 재시도 가능: FAILED, attemptCount=1, nextAttemptAt 설정
    // ──────────────────────────────────────────────────────────
    @Test
    void dispatch_failure_retry() {
        when(channel.name()).thenReturn("IN_APP");
        OutboxDispatcher dispatcher = buildDispatcher();

        NotificationOutbox row = pendingRow();
        when(outboxRepo.findDueForDispatch(any(OffsetDateTime.class))).thenReturn(List.of(row));
        when(channel.enabled()).thenReturn(true);
        when(channel.deliver(row)).thenReturn(DeliveryResult.fail("smtp timeout"));
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dispatcher.dispatch();

        assertThat(row.getStatus()).isEqualTo("FAILED");
        assertThat(row.getAttemptCount()).isEqualTo(1);
        assertThat(row.getNextAttemptAt()).isNotNull();
        // attemptCount=1 → 1분 백오프
        OffsetDateTime expected = OffsetDateTime.now(FIXED_CLOCK).plusMinutes(1);
        assertThat(row.getNextAttemptAt()).isEqualTo(expected);
        verify(auditService, times(1)).log(any());
    }

    // ──────────────────────────────────────────────────────────
    // 3. maxRetries 도달 → DEAD + DLQ 저장
    // ──────────────────────────────────────────────────────────
    @Test
    void dispatch_max_retries_to_dlq() {
        when(channel.name()).thenReturn("IN_APP");
        OutboxDispatcher dispatcher = buildDispatcher();

        NotificationOutbox row = pendingRow();
        row.setAttemptCount(2); // 실패 후 3이 되어 maxRetries(3) 도달 → DLQ
        when(outboxRepo.findDueForDispatch(any(OffsetDateTime.class))).thenReturn(List.of(row));
        when(channel.enabled()).thenReturn(true);
        when(channel.deliver(row)).thenReturn(DeliveryResult.fail("permanent error"));
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dispatcher.dispatch();

        assertThat(row.getStatus()).isEqualTo("DEAD");
        ArgumentCaptor<NotificationDeadLetter> dlqCaptor =
                ArgumentCaptor.forClass(NotificationDeadLetter.class);
        verify(dlqRepo, times(1)).save(dlqCaptor.capture());
        NotificationDeadLetter saved = dlqCaptor.getValue();
        assertThat(saved.getErrorHistory()).contains("permanent error");
        verify(auditService, atLeastOnce()).log(any());
    }

    // ──────────────────────────────────────────────────────────
    // 4. 이미 DELIVERED 상태 → 채널 호출 없음 (idempotency)
    // ──────────────────────────────────────────────────────────
    @Test
    void dispatch_idempotent() {
        when(channel.name()).thenReturn("IN_APP");
        OutboxDispatcher dispatcher = buildDispatcher();

        NotificationOutbox row = pendingRow();
        row.setStatus("DELIVERED");
        when(outboxRepo.findDueForDispatch(any(OffsetDateTime.class))).thenReturn(List.of(row));

        dispatcher.dispatch();

        verify(channel, never()).deliver(any());
        verify(outboxRepo, never()).save(any());
        verify(auditService, never()).log(any());
    }

    // ──────────────────────────────────────────────────────────
    // 5. 채널 맵에 없는 채널 → 즉시 DLQ
    // ──────────────────────────────────────────────────────────
    @Test
    void dispatch_channel_not_available() {
        when(channel.name()).thenReturn("IN_APP"); // SMS는 맵에 없음
        OutboxDispatcher dispatcher = buildDispatcher();

        NotificationOutbox row = pendingRow();
        row.setChannel("SMS"); // 등록되지 않은 채널
        when(outboxRepo.findDueForDispatch(any(OffsetDateTime.class))).thenReturn(List.of(row));
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dispatcher.dispatch();

        assertThat(row.getStatus()).isEqualTo("DEAD");
        verify(dlqRepo, times(1)).save(any(NotificationDeadLetter.class));
        verify(channel, never()).deliver(any());
    }
}
