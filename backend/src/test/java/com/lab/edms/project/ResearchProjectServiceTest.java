package com.lab.edms.project;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.document.DocumentFile;
import com.lab.edms.document.DocumentFileRepository;
import com.lab.edms.project.dto.CreateProjectRequest;
import com.lab.edms.storage.RetentionResolver;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResearchProjectServiceTest {

    @Mock ResearchProjectRepository projectRepo;
    @Mock ResearchProjectTypeRepository typeRepo;
    @Mock DocumentFileRepository fileRepo;
    @Mock RetentionExtensionOutboxRepository outboxRepo;
    @Mock RetentionResolver retentionResolver;
    @Mock AuditService auditService;
    @Mock UserRepository userRepo;
    @Mock Authentication auth;

    private ResearchProjectService service;
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-13T00:00:00Z"), ZoneOffset.UTC);

    private User actor;
    private ResearchProjectType type;
    private ResearchProject activeProject;

    @BeforeEach
    void setUp() {
        service = new ResearchProjectService(
                projectRepo, typeRepo, fileRepo, outboxRepo,
                retentionResolver, auditService, userRepo, fixedClock);

        actor = new User();
        when(auth.getName()).thenReturn("testuser");
        when(userRepo.findByUserId("testuser")).thenReturn(Optional.of(actor));

        type = new ResearchProjectType();
        type.setTypeCode("CHEM");
        type.setTypeNameKr("화학신약");
        type.setRetentionYears(20);
        type.setPerpetual(false);

        activeProject = new ResearchProject();
        activeProject.setProjectCode("P-2026-001");
        activeProject.setProjectName("신약 A 임상");
        activeProject.setType(type);
        activeProject.setCreatedBy(actor);
        activeProject.setUpdatedBy(actor);

        // 기본적으로 빈 페이지 반환 (outbox 루프용)
        when(fileRepo.findByProjectCode(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(retentionResolver.resolveYears(any(ResearchProject.class))).thenReturn(20);
    }

    @Test
    void approve_happyPath() {
        when(projectRepo.findById("P-2026-001")).thenReturn(Optional.of(activeProject));

        ResearchProject result = service.approve("P-2026-001", LocalDate.of(2026, 6, 1), auth);

        assertThat(result.getStatus()).isEqualTo(ResearchProjectStatus.APPROVED);
        assertThat(result.getApprovalDate()).isEqualTo(LocalDate.of(2026, 6, 1));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).log(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.action()).isEqualTo(AuditAction.RESEARCH_PROJECT_APPROVED);
        assertThat(event.entityType()).isEqualTo("research_project");
        assertThat(event.entityId()).isEqualTo("P-2026-001");
        assertThat(event.afterValue()).isNotNull();
    }

    @Test
    void terminate_happyPath() {
        when(projectRepo.findById("P-2026-001")).thenReturn(Optional.of(activeProject));

        ResearchProject result = service.terminate("P-2026-001", LocalDate.of(2026, 12, 31), auth);

        assertThat(result.getStatus()).isEqualTo(ResearchProjectStatus.TERMINATED);
        assertThat(result.getTerminationDate()).isEqualTo(LocalDate.of(2026, 12, 31));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).log(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.action()).isEqualTo(AuditAction.RESEARCH_PROJECT_TERMINATED);
        assertThat(event.entityType()).isEqualTo("research_project");
        assertThat(event.entityId()).isEqualTo("P-2026-001");
        assertThat(event.afterValue()).isNotNull();
        // 파일이 없으므로 outbox save는 호출되지 않아야 함
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void approve_rejectsNonActive() {
        // 이미 APPROVED 상태인 프로젝트
        activeProject.approve(LocalDate.of(2026, 5, 1), actor,
                java.time.OffsetDateTime.now(fixedClock));
        when(projectRepo.findById("P-2026-001")).thenReturn(Optional.of(activeProject));

        assertThatThrownBy(() -> service.approve("P-2026-001", LocalDate.of(2026, 6, 1), auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void approve_rejectsTerminated() {
        // 이미 TERMINATED 상태인 프로젝트
        activeProject.terminate(LocalDate.of(2026, 4, 1), actor,
                java.time.OffsetDateTime.now(fixedClock));
        when(projectRepo.findById("P-2026-001")).thenReturn(Optional.of(activeProject));

        assertThatThrownBy(() -> service.approve("P-2026-001", LocalDate.of(2026, 6, 1), auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TERMINATED");
    }

    @Test
    void create_happyPath() {
        when(typeRepo.findById("CHEM")).thenReturn(Optional.of(type));
        when(projectRepo.save(any(ResearchProject.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateProjectRequest req = new CreateProjectRequest("P-2026-002", "신약 B 임상", "CHEM");
        ResearchProject result = service.create(req, auth);

        assertThat(result.getProjectCode()).isEqualTo("P-2026-002");
        assertThat(result.getProjectName()).isEqualTo("신약 B 임상");
        assertThat(result.getStatus()).isEqualTo(ResearchProjectStatus.ACTIVE);
        verify(projectRepo, times(1)).save(any(ResearchProject.class));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).log(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.action()).isEqualTo(AuditAction.RESEARCH_PROJECT_REGISTERED);
        assertThat(event.entityType()).isEqualTo("research_project");
        assertThat(event.entityId()).isEqualTo("P-2026-002");
        assertThat(event.afterValue()).isNotNull();
    }

    @Test
    void approve_perpetualProject_usesMaxYears() {
        // is_perpetual=true 타입 설정
        ResearchProjectType perpetualType = new ResearchProjectType();
        perpetualType.setTypeCode("PERP");
        perpetualType.setTypeNameKr("영구보존");
        perpetualType.setRetentionYears(null);
        perpetualType.setPerpetual(true);

        ResearchProject perpetualProject = new ResearchProject();
        perpetualProject.setProjectCode("P-PERP-001");
        perpetualProject.setProjectName("영구 보존 프로젝트");
        perpetualProject.setType(perpetualType);
        perpetualProject.setCreatedBy(actor);
        perpetualProject.setUpdatedBy(actor);

        when(projectRepo.findById("P-PERP-001")).thenReturn(Optional.of(perpetualProject));
        // 영구 프로젝트에 대해 99년 반환
        when(retentionResolver.resolveYears(any(ResearchProject.class))).thenReturn(99);

        service.approve("P-PERP-001", LocalDate.of(2026, 6, 1), auth);

        // resolveYears가 호출되었는지 확인
        verify(retentionResolver, times(1)).resolveYears(any(ResearchProject.class));
        // outbox.save는 파일이 없으므로 0번 호출
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void approve_withFiles_savesOutboxEntries() {
        DocumentFile file1 = mock(DocumentFile.class);
        when(file1.getId()).thenReturn(1L);
        when(file1.getMinioBucket()).thenReturn("test-bucket");
        when(file1.getMinioKey()).thenReturn("test/key/file1.pdf");

        DocumentFile file2 = mock(DocumentFile.class);
        when(file2.getId()).thenReturn(2L);
        when(file2.getMinioBucket()).thenReturn("test-bucket");
        when(file2.getMinioKey()).thenReturn("test/key/file2.pdf");

        // 첫 번째 페이지에 2개 파일, 두 번째 페이지 요청에서 빈 페이지 반환
        when(fileRepo.findByProjectCode(eq("P-2026-001"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(file1, file2)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        when(projectRepo.findById("P-2026-001")).thenReturn(Optional.of(activeProject));

        service.approve("P-2026-001", LocalDate.of(2026, 6, 1), auth);

        verify(outboxRepo, times(2)).save(any(RetentionExtensionOutbox.class));
    }
}
