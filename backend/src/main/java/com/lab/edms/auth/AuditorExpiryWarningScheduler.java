package com.lab.edms.auth;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.notification.EmailNotificationService;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class AuditorExpiryWarningScheduler {

    private final UserRepository userRepo;
    private final EmailNotificationService email;
    private final AuditService audit;

    public AuditorExpiryWarningScheduler(UserRepository userRepo,
                                         EmailNotificationService email, AuditService audit) {
        this.userRepo = userRepo;
        this.email = email;
        this.audit = audit;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void runWarningTick() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<User> warn = userRepo.findUsersExpiringOn(tomorrow);
        for (User u : warn) {
            email.sendAuditorExpiryWarning(u.getEmail(), u.getUserId(), u.getValidUntil());
            audit.log(new AuditEvent(null, AuditAction.AUDITOR_EXPIRY_WARNING, "USER",
                    String.valueOf(u.getId()), null, null,
                    "expiry warning for " + u.getValidUntil(),
                    null, OffsetDateTime.now(ZoneOffset.UTC)));
        }
    }
}
