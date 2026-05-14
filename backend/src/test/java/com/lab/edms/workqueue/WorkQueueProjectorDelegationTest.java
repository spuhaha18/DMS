package com.lab.edms.workqueue;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.delegation.Delegation;
import com.lab.edms.delegation.DelegationService;
import com.lab.edms.document.DocumentVersionRepository;
import com.lab.edms.user.UserRepository;
import com.lab.edms.workflow.event.WorkflowSubmittedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkQueueProjectorDelegationTest {

    @Mock
    private WorkQueueRepository repo;

    @Mock
    private DocumentVersionRepository documentVersionRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private AuditService auditService;

    @Mock
    private DelegationService delegationService;

    @InjectMocks
    private WorkQueueProjector projector;

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    /** 공통 이벤트 팩토리 */
    private WorkflowSubmittedEvent event(List<Long> reviewerIds) {
        return new WorkflowSubmittedEvent(
                10L,   // workflowInstanceId
                20L,   // documentVersionId
                30L,   // documentId
                "user-1",  // submittedByUserId
                reviewerIds,
                NOW
        );
    }

    /** Delegation 목 객체 생성 */
    private Delegation mockDelegation(Long id, Long delegateUserId) {
        Delegation d = mock(Delegation.class);
        when(d.getId()).thenReturn(id);
        when(d.getDelegateUserId()).thenReturn(delegateUserId);
        return d;
    }

    /**
     * TC-1: 위임 없음 → reviewer에게 아이템 1개만 저장
     */
    @Test
    void onSubmitted_no_delegation_creates_single_item() {
        // given
        when(documentVersionRepo.findById(20L)).thenReturn(Optional.empty());
        when(delegationService.findActiveDelegatesFor(eq(100L), any(OffsetDateTime.class)))
                .thenReturn(Collections.emptyList());

        WorkflowSubmittedEvent e = event(List.of(100L));

        // when
        projector.onSubmitted(e);

        // then
        verify(repo, times(1)).save(any(WorkQueueItem.class));
        verify(auditService, times(1)).log(argThat(ae ->
                ae.action() == AuditAction.WORK_QUEUE_ITEM_OPENED));
        // DELEGATION_USED 감사 로그 없어야 함
        verify(auditService, never()).log(argThat(ae ->
                ae.action() == AuditAction.DELEGATION_USED));
    }

    /**
     * TC-2: 활성 위임 1건 → 아이템 2개(원본 + 위임), DELEGATION_USED 감사
     */
    @Test
    void onSubmitted_single_delegation_creates_two_items() {
        // given
        when(documentVersionRepo.findById(20L)).thenReturn(Optional.empty());
        Delegation delegation = mockDelegation(999L, 200L);
        when(delegationService.findActiveDelegatesFor(eq(100L), any(OffsetDateTime.class)))
                .thenReturn(List.of(delegation));

        WorkflowSubmittedEvent e = event(List.of(100L));

        // when
        projector.onSubmitted(e);

        // then — repo.save 2번
        verify(repo, times(2)).save(any(WorkQueueItem.class));

        // DELEGATION_USED 감사 1번, 감사 이벤트에 delegation id 포함
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, atLeast(1)).log(auditCaptor.capture());

        List<AuditEvent> delegationUsedEvents = auditCaptor.getAllValues().stream()
                .filter(ae -> ae.action() == AuditAction.DELEGATION_USED)
                .toList();
        assertThat(delegationUsedEvents).hasSize(1);
        assertThat(delegationUsedEvents.get(0).entityId()).isEqualTo("999");
        assertThat(delegationUsedEvents.get(0).entityType()).isEqualTo("delegations");
    }

    /**
     * TC-3: 위임 만료(서비스가 빈 리스트 반환) → 아이템 1개만 저장
     */
    @Test
    void onSubmitted_delegation_expired_not_included() {
        // given — 서비스가 만료 필터링 후 빈 리스트 반환
        when(documentVersionRepo.findById(20L)).thenReturn(Optional.empty());
        when(delegationService.findActiveDelegatesFor(eq(100L), any(OffsetDateTime.class)))
                .thenReturn(Collections.emptyList());

        WorkflowSubmittedEvent e = event(List.of(100L));

        // when
        projector.onSubmitted(e);

        // then
        verify(repo, times(1)).save(any(WorkQueueItem.class));
        verify(auditService, never()).log(argThat(ae ->
                ae.action() == AuditAction.DELEGATION_USED));
    }

    /**
     * TC-4: reviewer 2명, 각각 위임 1건 → 아이템 4개 (2 원본 + 2 위임)
     */
    @Test
    void onSubmitted_multiple_reviewers_each_checked() {
        // given
        when(documentVersionRepo.findById(20L)).thenReturn(Optional.empty());

        Delegation del1 = mockDelegation(11L, 201L);
        Delegation del2 = mockDelegation(22L, 202L);

        when(delegationService.findActiveDelegatesFor(eq(101L), any(OffsetDateTime.class)))
                .thenReturn(List.of(del1));
        when(delegationService.findActiveDelegatesFor(eq(102L), any(OffsetDateTime.class)))
                .thenReturn(List.of(del2));

        WorkflowSubmittedEvent e = event(List.of(101L, 102L));

        // when
        projector.onSubmitted(e);

        // then — 총 4번 save
        verify(repo, times(4)).save(any(WorkQueueItem.class));

        // DELEGATION_USED 2번
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, atLeast(1)).log(auditCaptor.capture());

        long delegationUsedCount = auditCaptor.getAllValues().stream()
                .filter(ae -> ae.action() == AuditAction.DELEGATION_USED)
                .count();
        assertThat(delegationUsedCount).isEqualTo(2);
    }
}
