package com.lab.edms.training;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.common.ForbiddenException;
import com.lab.edms.common.NotFoundException;
import com.lab.edms.document.Document;
import com.lab.edms.document.DocumentRepository;
import com.lab.edms.document.DocumentVersion;
import com.lab.edms.document.DocumentVersionRepository;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.user.UserStatus;
import com.lab.edms.workflow.event.EffectiveTransitionedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class TrainingService {

    private static final Logger log = LoggerFactory.getLogger(TrainingService.class);

    /** 시스템이 직접 할당할 때 사용하는 가상 사용자 ID (DB에 없으므로 -1L) */
    static final Long SYSTEM_USER_ID = -1L;

    private final TrainingAssignmentRepository repo;
    private final UserRepository userRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentRepository documentRepository;
    private final AuditService auditService;

    public TrainingService(TrainingAssignmentRepository repo,
                           UserRepository userRepository,
                           DocumentVersionRepository versionRepository,
                           DocumentRepository documentRepository,
                           AuditService auditService) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.versionRepository = versionRepository;
        this.documentRepository = documentRepository;
        this.auditService = auditService;
    }

    /**
     * EffectiveTransitionedEvent 발생 시 모든 활성 사용자에게 교육 과제 생성.
     * 이미 할당된 경우 스킵 (idempotent).
     */
    @Transactional
    public void createAssignmentsForVersion(EffectiveTransitionedEvent event) {
        Long versionId = event.documentVersionId();
        OffsetDateTime dueAt = OffsetDateTime.now().plusDays(14);

        List<User> allUsers = userRepository.findAll();
        int created = 0;
        for (User user : allUsers) {
            if (user.getStatus() != UserStatus.ACTIVE) {
                continue;
            }
            Long userId = user.getId();
            if (repo.existsByUserIdAndVersionId(userId, versionId)) {
                continue;
            }
            TrainingAssignment assignment = new TrainingAssignment(userId, versionId, SYSTEM_USER_ID, dueAt);
            repo.save(assignment);
            created++;
        }
        log.info("TrainingService: versionId={} 에 대해 {}건 교육 과제 생성", versionId, created);
    }

    /**
     * 특정 사용자의 교육 과제 목록 반환.
     */
    @Transactional(readOnly = true)
    public List<TrainingAssignmentDto> listForUser(Long userId) {
        List<TrainingAssignment> assignments = repo.findByUserId(userId);
        return assignments.stream()
                .map(a -> {
                    String docNumber = "";
                    String docTitle = "";
                    DocumentVersion version = versionRepository.findById(a.getVersionId()).orElse(null);
                    if (version != null) {
                        docTitle = version.getTitle() != null ? version.getTitle() : "";
                        Document document = documentRepository.findById(version.getDocumentId()).orElse(null);
                        if (document != null) {
                            docNumber = document.getDocNumber() != null ? document.getDocNumber() : "";
                        }
                    }
                    return TrainingAssignmentDto.from(a, docNumber, docTitle);
                })
                .toList();
    }

    /**
     * 특정 버전에 대한 교육 현황 반환 (관리자용).
     */
    @Transactional(readOnly = true)
    public List<TrainingStatusDto> statusByVersion(Long versionId) {
        List<TrainingAssignment> assignments = repo.findByVersionId(versionId);
        long total = repo.countByVersionId(versionId);
        long completed = repo.countCompletedByVersionId(versionId);
        double completionRate = total == 0 ? 0.0 : (double) completed / total;

        return assignments.stream()
                .map(a -> {
                    String displayName = "";
                    User user = userRepository.findById(a.getUserId()).orElse(null);
                    if (user != null) {
                        displayName = user.getFullName() != null ? user.getFullName() : user.getUserId();
                    }
                    return new TrainingStatusDto(
                            a.getId(),
                            a.getUserId(),
                            displayName,
                            a.getAssignedAt(),
                            a.getCompletedAt(),
                            a.isCompleted(),
                            completionRate
                    );
                })
                .toList();
    }

    /**
     * 교육 이수 확인 (acknowledge).
     * 본인만 가능하며, 이미 완료된 경우 현재 상태를 반환 (idempotent).
     */
    @Transactional
    public TrainingAssignmentDto acknowledge(Long assignmentId, Authentication auth, String clientIp) {
        TrainingAssignment assignment = repo.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("TrainingAssignment not found: " + assignmentId));

        // 현재 사용자 조회
        String actorUserId = auth.getName();
        User actor = userRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new NotFoundException("User not found: " + actorUserId));

        // 본인 확인
        if (!assignment.getUserId().equals(actor.getId())) {
            throw new ForbiddenException("교육 과제는 본인만 확인할 수 있습니다.");
        }

        // 이미 완료된 경우 idempotent 처리
        if (assignment.isCompleted()) {
            return buildDto(assignment);
        }

        // 완료 처리
        // completionSigId is null for Phase 1 (button click only); Phase 2 will integrate e-signature flow
        assignment.complete(OffsetDateTime.now(), null);
        repo.save(assignment);

        // 감사 로그 기록
        auditService.log(
                AuditEvent.of(actorUserId, AuditAction.TRAINING_ACKNOWLEDGED)
                        .entity("TRAINING_ASSIGNMENT", String.valueOf(assignmentId))
                        .ip(clientIp)
                        .build()
        );

        return buildDto(assignment);
    }

    private TrainingAssignmentDto buildDto(TrainingAssignment assignment) {
        String docNumber = "";
        String docTitle = "";
        DocumentVersion version = versionRepository.findById(assignment.getVersionId()).orElse(null);
        if (version != null) {
            docTitle = version.getTitle() != null ? version.getTitle() : "";
            Document document = documentRepository.findById(version.getDocumentId()).orElse(null);
            if (document != null) {
                docNumber = document.getDocNumber() != null ? document.getDocNumber() : "";
            }
        }
        return TrainingAssignmentDto.from(assignment, docNumber, docTitle);
    }
}
