package com.lab.edms.auth;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.user.UserStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class AccountUnlockScheduler {

    private static final int LOCKOUT_MINUTES = 30;

    private final UserRepository userRepo;
    private final AuditService audit;

    public AccountUnlockScheduler(UserRepository userRepo, AuditService audit) {
        this.userRepo = userRepo;
        this.audit = audit;
    }

    @Scheduled(fixedDelay = 60_000L)
    @Transactional
    public void runUnlockTick() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(LOCKOUT_MINUTES);
        List<User> locked = userRepo.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.LOCKED
                        && u.getLockedAt() != null
                        && u.getLockedAt().isBefore(cutoff))
                .toList();

        for (User u : locked) {
            u.setStatus(UserStatus.ACTIVE);
            u.setFailedAttempts(0);
            u.setLockedAt(null);
            userRepo.save(u);
            audit.log(new AuditEvent(null, AuditAction.ACCOUNT_AUTO_UNLOCKED, "USER",
                    String.valueOf(u.getId()), null, null,
                    "auto unlock after " + LOCKOUT_MINUTES + " minutes",
                    null, OffsetDateTime.now(ZoneOffset.UTC)));
        }
    }
}
