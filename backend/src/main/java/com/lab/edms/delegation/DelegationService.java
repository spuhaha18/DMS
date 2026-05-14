package com.lab.edms.delegation;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.common.ForbiddenException;
import com.lab.edms.common.NotFoundException;
import com.lab.edms.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class DelegationService {

    private final DelegationRepository delegationRepo;
    private final UserRepository userRepo;
    private final AuditService auditService;

    public DelegationService(DelegationRepository delegationRepo,
                             UserRepository userRepo,
                             AuditService auditService) {
        this.delegationRepo = delegationRepo;
        this.userRepo = userRepo;
        this.auditService = auditService;
    }

    // ── Create ──────────────────────────────────────────────────────────

    public Delegation request(Long delegatorUserId, Long delegateUserId,
                              String scopeKind, String scopeValue,
                              String reason,
                              OffsetDateTime validFrom, OffsetDateTime validTo) {
        if (delegatorUserId.equals(delegateUserId)) {
            throw new IllegalArgumentException("Delegator and delegate must be different users");
        }
        if (!validTo.isAfter(validFrom)) {
            throw new IllegalArgumentException("validTo must be after validFrom");
        }
        if (!userRepo.existsById(delegatorUserId)) {
            throw new NotFoundException("Delegator user not found: " + delegatorUserId);
        }
        if (!userRepo.existsById(delegateUserId)) {
            throw new NotFoundException("Delegate user not found: " + delegateUserId);
        }

        Delegation delegation = new Delegation();
        delegation.setDelegatorUserId(delegatorUserId);
        delegation.setDelegateUserId(delegateUserId);
        delegation.setScopeKind(scopeKind);
        delegation.setScopeValue(scopeValue);
        delegation.setReason(reason);
        delegation.setValidFrom(validFrom);
        delegation.setValidTo(validTo);
        delegation.setState("REQUESTED");

        Delegation saved = delegationRepo.save(delegation);

        auditService.log(AuditEvent.of(String.valueOf(delegatorUserId), AuditAction.DELEGATION_REQUESTED)
                .entity("delegations", String.valueOf(saved.getId()))
                .build());

        return saved;
    }

    // ── QA Manager actions ──────────────────────────────────────────────

    public Delegation approve(Long delegationId, Long qaApproverUserId) {
        Delegation delegation = loadOrThrow(delegationId);
        if (!"REQUESTED".equals(delegation.getState())) {
            throw new IllegalStateException("Delegation must be in REQUESTED state to approve, current state: " + delegation.getState());
        }

        delegation.setState("APPROVED");
        delegation.setQaApproverUserId(qaApproverUserId);
        delegation.setQaApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));

        Delegation saved = delegationRepo.save(delegation);

        auditService.log(AuditEvent.of(String.valueOf(qaApproverUserId), AuditAction.DELEGATION_APPROVED)
                .entity("delegations", String.valueOf(saved.getId()))
                .build());

        return saved;
    }

    public Delegation reject(Long delegationId, String reason, Long qaApproverUserId) {
        Delegation delegation = loadOrThrow(delegationId);
        if (!"REQUESTED".equals(delegation.getState())) {
            throw new IllegalStateException("Delegation must be in REQUESTED state to reject, current state: " + delegation.getState());
        }

        delegation.setState("REJECTED");
        delegation.setQaApproverUserId(qaApproverUserId);
        delegation.setQaRejectionReason(reason);

        Delegation saved = delegationRepo.save(delegation);

        auditService.log(AuditEvent.of(String.valueOf(qaApproverUserId), AuditAction.DELEGATION_REJECTED)
                .entity("delegations", String.valueOf(saved.getId()))
                .reason(reason)
                .build());

        return saved;
    }

    // ── Revoke ──────────────────────────────────────────────────────────

    public Delegation revoke(Long delegationId, Long byUserId) {
        Delegation delegation = loadOrThrow(delegationId);

        String state = delegation.getState();
        if (!"REQUESTED".equals(state) && !"APPROVED".equals(state)) {
            throw new IllegalStateException("Only REQUESTED or APPROVED delegations can be revoked, current state: " + state);
        }

        boolean isDelegator = delegation.getDelegatorUserId().equals(byUserId);
        boolean isQa = isQaManager(byUserId);
        if (!isDelegator && !isQa) {
            throw new ForbiddenException("Only the delegator or a QA Manager can revoke a delegation");
        }

        delegation.setState("REVOKED");
        delegation.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
        delegation.setRevokedByUserId(byUserId);

        Delegation saved = delegationRepo.save(delegation);

        auditService.log(AuditEvent.of(String.valueOf(byUserId), AuditAction.DELEGATION_REVOKED)
                .entity("delegations", String.valueOf(saved.getId()))
                .build());

        return saved;
    }

    // ── Queries ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Delegation> findActiveDelegatesFor(Long delegatorUserId, OffsetDateTime at) {
        return delegationRepo.findActiveDelegationsForDelegator(delegatorUserId, at);
    }

    @Transactional(readOnly = true)
    public List<Delegation> findActiveDelegationsByUser(Long userId, OffsetDateTime at) {
        return delegationRepo.findByDelegateUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(d -> d.isActive(at))
                .collect(Collectors.toList());
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    private boolean isQaManager(Long userId) {
        return userRepo.findByIdWithRoles(userId)
                .map(u -> u.getRoles().stream()
                        .anyMatch(r -> "QA_MANAGER".equals(r.getRole().getRoleCode())))
                .orElse(false);
    }

    private Delegation loadOrThrow(Long id) {
        return delegationRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Delegation not found: " + id));
    }
}
