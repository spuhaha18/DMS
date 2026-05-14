package com.lab.edms.notification;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Component
public class NotificationArchiveJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationArchiveJob.class);
    private static final int ARCHIVE_AFTER_DAYS = 90;

    private final NotificationRepository notificationRepo;
    private final NotificationArchivedRepository archivedRepo;
    private final AuditService auditService;
    private final Clock clock;

    public NotificationArchiveJob(NotificationRepository notificationRepo,
                                   NotificationArchivedRepository archivedRepo,
                                   AuditService auditService,
                                   Clock clock) {
        this.notificationRepo = notificationRepo;
        this.archivedRepo = archivedRepo;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "NotificationArchiveJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public void archive() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusDays(ARCHIVE_AFTER_DAYS);
        List<Notification> toArchive = notificationRepo.findByIsReadTrueAndCreatedAtBefore(cutoff);

        if (toArchive.isEmpty()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        for (Notification n : toArchive) {
            NotificationArchived archived = new NotificationArchived();
            archived.setId(n.getId());
            archived.setRecipientId(n.getRecipientId());
            archived.setEventCode(n.getEventCode());
            archived.setTitle(n.getTitle());
            archived.setBody(n.getBody());
            archived.setRead(n.isRead());
            archived.setReadAt(n.getReadAt());
            archived.setLinkPath(n.getLinkPath());
            archived.setRelatedDocumentId(n.getRelatedDocumentId());
            archived.setRelatedVersionId(n.getRelatedVersionId());
            archived.setRelatedWorkItemId(n.getRelatedWorkItemId());
            archived.setSeverity(n.getSeverity());
            archived.setCreatedAt(n.getCreatedAt());
            archived.setArchivedAt(now);
            archivedRepo.save(archived);
        }

        notificationRepo.deleteAll(toArchive);

        log.info("NotificationArchiveJob: archived {} notifications (cutoff={})", toArchive.size(), cutoff);
        auditService.log(AuditEvent.of("SYSTEM", AuditAction.NOTIFICATION_ARCHIVED)
                .entity("notifications", "count=" + toArchive.size())
                .build());
    }
}
