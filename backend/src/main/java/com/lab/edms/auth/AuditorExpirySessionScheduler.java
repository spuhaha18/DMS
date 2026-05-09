package com.lab.edms.auth;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.user.UserStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class AuditorExpirySessionScheduler {

    private final UserRepository userRepo;
    private final AuditService audit;
    private final SessionRegistry sessionRegistry;

    public AuditorExpirySessionScheduler(UserRepository userRepo, AuditService audit,
                                         SessionRegistry sessionRegistry) {
        this.userRepo = userRepo;
        this.audit = audit;
        this.sessionRegistry = sessionRegistry;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void runExpiryTick() {
        List<User> expired = userRepo.findExpiredAuditors();
        for (User u : expired) {
            u.setStatus(UserStatus.DISABLED);
            userRepo.save(u);
            terminate(u.getUserId());
            audit.log(new AuditEvent(null, AuditAction.AUDITOR_EXPIRED, "USER",
                    String.valueOf(u.getId()), null, null,
                    "valid_until reached: " + u.getValidUntil(),
                    null, OffsetDateTime.now(ZoneOffset.UTC)));
        }
    }

    private void terminate(String userId) {
        for (Object p : sessionRegistry.getAllPrincipals()) {
            if (p == null || !p.toString().equals(userId)) continue;
            for (SessionInformation si : sessionRegistry.getAllSessions(p, false)) si.expireNow();
        }
    }
}
