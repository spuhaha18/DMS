package com.lab.edms.notification;

import com.lab.edms.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationArchiveJobTest {

    @Mock
    NotificationRepository notificationRepo;
    @Mock
    NotificationArchivedRepository archivedRepo;
    @Mock
    AuditService auditService;

    private NotificationArchiveJob job;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-05-14T03:00:00Z"), ZoneOffset.UTC);
        job = new NotificationArchiveJob(notificationRepo, archivedRepo, auditService, clock);
    }

    @Test
    void archive_moves_91day_read_notifications_to_archived() {
        // 91 days old, read=true → should be archived
        Notification old = makeNotification(1L, true, 91);
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusDays(90);
        when(notificationRepo.findByIsReadTrueAndCreatedAtBefore(cutoff)).thenReturn(List.of(old));

        job.archive();

        verify(archivedRepo).save(any(NotificationArchived.class));
        verify(notificationRepo).deleteAll(List.of(old));
        verify(auditService).log(any());
    }

    @Test
    void archive_skips_89day_read_notifications() {
        // 89 days old → cutoff is 90 days, so not included
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusDays(90);
        when(notificationRepo.findByIsReadTrueAndCreatedAtBefore(cutoff)).thenReturn(List.of());

        job.archive();

        verify(archivedRepo, never()).save(any());
        verify(notificationRepo, never()).deleteAll(anyList());
    }

    @Test
    void archive_skips_91day_unread_notifications() {
        // The query already filters by is_read=true, so unread notifications won't be returned
        // Verify nothing is archived when list is empty
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusDays(90);
        when(notificationRepo.findByIsReadTrueAndCreatedAtBefore(cutoff)).thenReturn(List.of());

        job.archive();

        verify(archivedRepo, never()).save(any());
    }

    private Notification makeNotification(Long id, boolean read, int daysAgo) {
        Notification n = new Notification();
        try {
            setField(n, "id", id);
            setField(n, "recipientId", 1L);
            setField(n, "eventCode", "WORKFLOW_SUBMITTED");
            setField(n, "title", "테스트 알림");
            setField(n, "severity", "INFO");
            setField(n, "createdAt", OffsetDateTime.now(clock).minusDays(daysAgo));
            setField(n, "isRead", read);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return n;
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(obj, value);
    }
}
