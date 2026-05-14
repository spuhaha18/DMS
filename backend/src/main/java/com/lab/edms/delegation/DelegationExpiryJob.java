package com.lab.edms.delegation;

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
public class DelegationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(DelegationExpiryJob.class);

    private final DelegationRepository delegationRepo;
    private final AuditService auditService;
    private final Clock clock;

    public DelegationExpiryJob(DelegationRepository delegationRepo,
                              AuditService auditService,
                              Clock clock) {
        this.delegationRepo = delegationRepo;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Scheduled(fixedRate = 3600000)  // every 1 hour
    @SchedulerLock(name = "DelegationExpiryJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    @Transactional
    public void expireOverdue() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Delegation> expired = delegationRepo.findExpiredApproved(now);

        if (expired.isEmpty()) {
            return;
        }

        for (Delegation d : expired) {
            d.setState("EXPIRED");
            delegationRepo.save(d);
            auditService.log(AuditEvent.of("SYSTEM", AuditAction.DELEGATION_REVOKED)
                    .entity("delegations", String.valueOf(d.getId()))
                    .build());
        }

        log.info("DelegationExpiryJob: expired {} delegations", expired.size());
    }
}
