package com.lab.edms.training;

import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.common.ForbiddenException;
import com.lab.edms.document.DocumentRepository;
import com.lab.edms.document.DocumentVersionRepository;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.user.UserStatus;
import com.lab.edms.workflow.event.EffectiveTransitionedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingServiceTest {

    @Mock
    TrainingAssignmentRepository repo;

    @Mock
    UserRepository userRepository;

    @Mock
    DocumentVersionRepository versionRepository;

    @Mock
    DocumentRepository documentRepository;

    @Mock
    AuditService auditService;

    @InjectMocks
    TrainingService trainingService;

    private EffectiveTransitionedEvent makeEvent(Long versionId) {
        return new EffectiveTransitionedEvent(
                1L, versionId, 10L, 100L,
                LocalDate.now(), OffsetDateTime.now()
        );
    }

    private User makeUser(Long id) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        return user;
    }

    @Test
    void createAssignments_createsForAllNonDuplicateUsers() {
        // given
        Long versionId = 42L;
        EffectiveTransitionedEvent event = makeEvent(versionId);

        User user1 = makeUser(1L);
        User user2 = makeUser(2L);
        when(userRepository.findAll()).thenReturn(List.of(user1, user2));
        when(repo.existsByUserIdAndVersionId(1L, versionId)).thenReturn(false);
        when(repo.existsByUserIdAndVersionId(2L, versionId)).thenReturn(false);
        when(repo.save(any(TrainingAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        trainingService.createAssignmentsForVersion(event);

        // then
        verify(repo, times(2)).save(any(TrainingAssignment.class));
    }

    @Test
    void createAssignments_skipsDuplicates() {
        // given
        Long versionId = 43L;
        EffectiveTransitionedEvent event = makeEvent(versionId);

        User user1 = makeUser(1L);
        User user2 = makeUser(2L);
        when(userRepository.findAll()).thenReturn(List.of(user1, user2));
        // user1은 이미 할당됨
        when(repo.existsByUserIdAndVersionId(1L, versionId)).thenReturn(true);
        when(repo.existsByUserIdAndVersionId(2L, versionId)).thenReturn(false);
        when(repo.save(any(TrainingAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        trainingService.createAssignmentsForVersion(event);

        // then: user2만 저장
        verify(repo, times(1)).save(any(TrainingAssignment.class));
    }

    @Test
    void acknowledge_throwsForbiddenForOtherUser() {
        // given
        Long assignmentId = 99L;

        // 과제는 userId=10L 에게 할당
        TrainingAssignment assignment = new TrainingAssignment(10L, 1L, TrainingService.SYSTEM_USER_ID, OffsetDateTime.now().plusDays(14));
        when(repo.findById(assignmentId)).thenReturn(Optional.of(assignment));

        // 인증 사용자는 userId=20L ("otherUser")
        User otherUser = makeUser(20L);
        when(userRepository.findByUserId("otherUser")).thenReturn(Optional.of(otherUser));

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("otherUser");

        // when / then
        assertThatThrownBy(() -> trainingService.acknowledge(assignmentId, auth, "127.0.0.1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void acknowledge_successfullyCompletesAssignment() {
        // given
        Long assignmentId = 77L;

        TrainingAssignment assignment = new TrainingAssignment(5L, 2L, TrainingService.SYSTEM_USER_ID, OffsetDateTime.now().plusDays(14));
        when(repo.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(repo.save(any(TrainingAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        User actor = makeUser(5L);
        when(userRepository.findByUserId("actor")).thenReturn(Optional.of(actor));

        // DocumentVersion / Document 없어도 DTO는 생성됨
        when(versionRepository.findById(2L)).thenReturn(Optional.empty());

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("actor");

        // when
        TrainingAssignmentDto dto = trainingService.acknowledge(assignmentId, auth, "10.0.0.1");

        // then
        assertThat(dto.completed()).isTrue();
        assertThat(dto.completedAt()).isNotNull();
        verify(auditService, times(1)).log(any(AuditEvent.class));
    }

    @Test
    void acknowledge_idempotentWhenAlreadyCompleted() {
        // given
        Long assignmentId = 55L;

        TrainingAssignment assignment = new TrainingAssignment(3L, 2L, TrainingService.SYSTEM_USER_ID, OffsetDateTime.now().plusDays(14));
        // 이미 완료 처리
        assignment.complete(OffsetDateTime.now().minusHours(1), null);

        when(repo.findById(assignmentId)).thenReturn(Optional.of(assignment));

        User actor = makeUser(3L);
        when(userRepository.findByUserId("actor2")).thenReturn(Optional.of(actor));
        when(versionRepository.findById(2L)).thenReturn(Optional.empty());

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("actor2");

        // when
        TrainingAssignmentDto dto = trainingService.acknowledge(assignmentId, auth, "127.0.0.1");

        // then: 저장 및 감사 로그 호출 없음
        verify(repo, never()).save(any());
        verify(auditService, never()).log(any());
        assertThat(dto.completed()).isTrue();
    }
}
