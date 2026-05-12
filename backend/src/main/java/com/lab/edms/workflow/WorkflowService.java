package com.lab.edms.workflow;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.category.DocumentCategory;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.common.ForbiddenException;
import com.lab.edms.common.NotFoundException;
import com.lab.edms.common.UnauthorizedException;
import com.lab.edms.common.UnprocessableEntityException;
import com.lab.edms.document.Document;
import com.lab.edms.document.DocumentRepository;
import com.lab.edms.document.DocumentVersion;
import com.lab.edms.document.DocumentVersionRepository;
import com.lab.edms.lifecycle.LifecycleStateMachine;
import com.lab.edms.pdf.PdfRenditionPipeline;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.workflow.dto.PendingTaskDto;
import com.lab.edms.workflow.dto.SubmitRequest;
import com.lab.edms.workflow.dto.SubmitResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowService {

    private final WorkflowInstanceRepository wfInstanceRepo;
    private final WorkflowStepInstanceRepository wfStepRepo;
    private final WorkflowTemplateRepository templateRepo;
    private final WorkflowTemplateStepRepository templateStepRepo;
    private final DocumentRepository documentRepo;
    private final DocumentVersionRepository documentVersionRepo;
    private final DocumentCategoryRepository categoryRepo;
    private final AssigneeResolver assigneeResolver;
    private final AuditService auditService;
    private final UserRepository userRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PdfRenditionPipeline pdfRenditionPipeline;

    // LifecycleStateMachine은 직접 전이 문자열로 처리하므로 여기서는 상태 문자열 비교만 사용
    @SuppressWarnings("unused")
    private final LifecycleStateMachine stateMachine;

    public WorkflowService(WorkflowInstanceRepository wfInstanceRepo,
                           WorkflowStepInstanceRepository wfStepRepo,
                           WorkflowTemplateRepository templateRepo,
                           WorkflowTemplateStepRepository templateStepRepo,
                           DocumentRepository documentRepo,
                           DocumentVersionRepository documentVersionRepo,
                           DocumentCategoryRepository categoryRepo,
                           AssigneeResolver assigneeResolver,
                           AuditService auditService,
                           UserRepository userRepo,
                           BCryptPasswordEncoder passwordEncoder,
                           LifecycleStateMachine stateMachine,
                           PdfRenditionPipeline pdfRenditionPipeline) {
        this.wfInstanceRepo = wfInstanceRepo;
        this.wfStepRepo = wfStepRepo;
        this.templateRepo = templateRepo;
        this.templateStepRepo = templateStepRepo;
        this.documentRepo = documentRepo;
        this.documentVersionRepo = documentVersionRepo;
        this.categoryRepo = categoryRepo;
        this.assigneeResolver = assigneeResolver;
        this.auditService = auditService;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.stateMachine = stateMachine;
        this.pdfRenditionPipeline = pdfRenditionPipeline;
    }

    /**
     * T-01: DRAFT → UNDER_REVIEW 전이.
     * 워크플로 인스턴스를 생성하고 첫 번째 step을 IN_PROGRESS로 설정합니다.
     */
    @Transactional
    public SubmitResponse submit(Long docId, Long verId,
                                 SubmitRequest req, Authentication auth) {
        String actorId = auth.getName();

        // 1. 문서/버전 조회
        DocumentVersion version = documentVersionRepo.findById(verId)
                .orElseThrow(() -> new NotFoundException("버전을 찾을 수 없음: " + verId));
        Document document = documentRepo.findById(docId)
                .orElseThrow(() -> new NotFoundException("문서를 찾을 수 없음: " + docId));

        // IDOR 방어: version이 이 document에 속하는지 확인
        if (!version.getDocumentId().equals(docId)) {
            throw new ForbiddenException("해당 버전은 이 문서에 속하지 않습니다");
        }

        // 2. 권한: 제출자는 version.createdBy와 동일해야 함
        User actor = getUserByUserId(actorId);
        if (!version.getCreatedBy().equals(actor.getId())) {
            throw new ForbiddenException("문서 소유자만 제출할 수 있습니다");
        }

        // 3. 상태 가드: DRAFT 상태인 버전만 제출 가능
        if (!"DRAFT".equals(version.getState())) {
            throw new UnprocessableEntityException("LIFECYCLE_002", "DRAFT 상태인 버전만 제출 가능합니다");
        }

        // 4. 파일 업로드 확인
        if (version.getSourceFileKey() == null || version.getSourceFileKey().isBlank()) {
            throw new UnprocessableEntityException("WORKFLOW_001", "파일을 먼저 업로드해야 합니다");
        }

        // 5. SELECT FOR UPDATE (TOCTOU 방지: 락 획득 후 활성 워크플로 확인)
        documentRepo.lockForUpdate(docId);

        // 6. 이미 진행 중인 워크플로 없는지 확인 (락 안에서)
        wfInstanceRepo.findActiveByVersion(verId).ifPresent(wi -> {
            throw new UnprocessableEntityException("WORKFLOW_002", "이미 진행 중인 결재가 있습니다");
        });

        // 7. 워크플로 템플릿 조회
        WorkflowTemplate template = templateRepo.findByCategoryIdAndActiveTrue(document.getCategoryId())
                .orElseThrow(() -> new UnprocessableEntityException("WORKFLOW_003", "해당 카테고리에 활성 워크플로 템플릿이 없습니다"));
        List<WorkflowTemplateStep> steps = templateStepRepo.findByTemplateIdOrderByStepOrder(template.getId());

        // 8. Assignees fixation
        Instant now = Instant.now();
        Map<Integer, List<String>> manualAssignees =
                req.manualAssignees() != null ? req.manualAssignees() : Map.of();
        List<List<AssigneeRef>> assigneesByStep = assigneeResolver.resolveAll(
                steps, document, manualAssignees, actorId, now);

        // 9. INSERT workflow_instances
        WorkflowInstance wfInstance = new WorkflowInstance();
        wfInstance.setVersionId(verId);
        wfInstance.setTemplateId(template.getId());
        wfInstance.setState("IN_PROGRESS");
        wfInstance.setCurrentStep(1);
        wfInstance.setStartedBy(actorId);
        wfInstanceRepo.save(wfInstance);

        // 10. INSERT workflow_step_instances (step_order=1만 IN_PROGRESS, 나머지 PENDING)
        for (int i = 0; i < steps.size(); i++) {
            WorkflowTemplateStep step = steps.get(i);
            WorkflowStepInstance stepInst = new WorkflowStepInstance();
            stepInst.setWorkflowId(wfInstance.getId());
            stepInst.setStepOrder(step.getStepOrder());
            stepInst.setStepType(step.getStepType());
            stepInst.setRoleCode(step.getRoleCode());
            stepInst.setMinSigners(step.getMinSigners());
            stepInst.setParallel(step.isParallel());
            stepInst.setQaRequired(step.isQaRequired());
            stepInst.setAssignees(assigneesByStep.get(i));
            stepInst.setSigned(new ArrayList<>());
            stepInst.setState(i == 0 ? "IN_PROGRESS" : "PENDING");
            if (i == 0) stepInst.setStartedAt(OffsetDateTime.now());
            wfStepRepo.save(stepInst);
        }

        // 11. 상태 전이 T-01: DRAFT → UNDER_REVIEW
        String prevState = version.getState();
        version.setState("UNDER_REVIEW");
        documentVersionRepo.save(version);

        // M7: UNDER_REVIEW 진입 시 INITIAL PDF 변환 trigger
        pdfRenditionPipeline.enqueueInitialConversion(document.getId());

        // 12. Audit 로그
        auditService.log(AuditEvent.of(actorId, AuditAction.WORKFLOW_SUBMITTED)
                .entity("document_version", String.valueOf(verId))
                .build());
        auditService.log(AuditEvent.of(actorId, AuditAction.STATE_TRANSITION)
                .entity("document_version", String.valueOf(verId))
                .before("\"" + prevState + "\"")
                .after("\"UNDER_REVIEW\"")
                .build());

        return new SubmitResponse(wfInstance.getId(), 1, assigneesByStep.get(0));
    }

    /**
     * T-04: UNDER_REVIEW 거부 → version DRAFT
     * T-05: UNDER_APPROVAL 거부 → version DRAFT
     */
    @Transactional
    public void reject(Long docId, Long verId, Long stepInstanceId, String reason,
                       String password, Authentication auth, String clientIp) {
        String actorUserId = auth.getName();

        // 1. PW 재인증
        if (!verifyPassword(actorUserId, password)) {
            auditService.log(AuditEvent.of(actorUserId, AuditAction.USER_LOGIN_FAIL)
                    .entity("USER", actorUserId)
                    .ip(clientIp)
                    .build());
            throw new UnauthorizedException("비밀번호가 올바르지 않습니다");
        }

        // 2. WorkflowStepInstance 조회 + IDOR 가드
        WorkflowStepInstance step = wfStepRepo.findById(stepInstanceId)
                .orElseThrow(() -> new NotFoundException("결재 단계를 찾을 수 없음: " + stepInstanceId));

        WorkflowInstance wf = wfInstanceRepo.findById(step.getWorkflowId())
                .orElseThrow(() -> new NotFoundException("워크플로를 찾을 수 없음: " + step.getWorkflowId()));

        if (!wf.getVersionId().equals(verId)) {
            throw new ForbiddenException("버전이 일치하지 않습니다");
        }

        // 3. 가드: step.state == 'IN_PROGRESS'
        if (!"IN_PROGRESS".equals(step.getState())) {
            throw new UnprocessableEntityException("WORKFLOW_012", "이미 처리된 단계입니다");
        }

        User actor = getUserByUserId(actorUserId);
        boolean isAssignee = step.getAssignees().stream()
                .anyMatch(a -> actorUserId.equals(a.userIdString()));
        if (!isAssignee) {
            throw new ForbiddenException("결재 권한이 없습니다");
        }

        // 4. reason 필수 검증
        if (reason == null || reason.isBlank()) {
            throw new UnprocessableEntityException("WORKFLOW_013", "반려 사유는 필수입니다");
        }

        DocumentVersion version = documentVersionRepo.findById(verId)
                .orElseThrow(() -> new NotFoundException("버전을 찾을 수 없음: " + verId));

        // IDOR 가드: version이 이 document에 속하는지 확인
        if (!version.getDocumentId().equals(docId)) {
            throw new ForbiddenException("해당 버전은 이 문서에 속하지 않습니다");
        }

        // 5. step.state = REJECTED
        step.setState("REJECTED");
        step.setRejectionReason(reason);
        step.setCompletedAt(OffsetDateTime.now());
        wfStepRepo.save(step);

        // 6. workflow.state = REJECTED
        wf.setState("REJECTED");
        wf.setCompletedAt(OffsetDateTime.now());
        wf.setCompletedBy(actorUserId);
        wfInstanceRepo.save(wf);

        // 7. version.state 결정
        String prevVersionState = version.getState();
        String newVersionState;
        if ("UNDER_REVIEW".equals(prevVersionState)) {
            newVersionState = "DRAFT";   // T-04
        } else if ("UNDER_APPROVAL".equals(prevVersionState)) {
            newVersionState = "DRAFT";   // T-05
        } else {
            throw new UnprocessableEntityException("LIFECYCLE_003",
                    "거부 불가한 상태입니다: " + prevVersionState);
        }

        // 8. UPDATE document_versions SET state='DRAFT'
        version.setState(newVersionState);
        documentVersionRepo.save(version);

        // 9. Audit: 3건
        auditService.log(AuditEvent.of(actorUserId, AuditAction.WORKFLOW_STEP_REJECTED)
                .entity("workflow_step_instance", String.valueOf(stepInstanceId))
                .reason(reason)
                .ip(clientIp)
                .build());
        auditService.log(AuditEvent.of(actorUserId, AuditAction.WORKFLOW_REJECTED)
                .entity("workflow_instance", String.valueOf(wf.getId()))
                .reason(reason)
                .ip(clientIp)
                .build());
        auditService.log(AuditEvent.of(actorUserId, AuditAction.STATE_TRANSITION)
                .entity("document_version", String.valueOf(verId))
                .before("\"" + prevVersionState + "\"")
                .after("\"" + newVersionState + "\"")
                .ip(clientIp)
                .build());
    }

    /**
     * 현재 step 완료 후 다음 step으로 전이하거나, 모두 완료 시 최종 전이를 수행합니다.
     * SignatureService에서 호출됩니다.
     */
    @Transactional
    public void advance(Long workflowId) {
        WorkflowInstance wf = wfInstanceRepo.findById(workflowId)
                .orElseThrow(() -> new NotFoundException("워크플로를 찾을 수 없음: " + workflowId));

        List<WorkflowStepInstance> steps = wfStepRepo.findOrderedByWorkflow(workflowId);

        // 다음 PENDING step 찾기
        WorkflowStepInstance nextPending = steps.stream()
                .filter(s -> "PENDING".equals(s.getState()))
                .findFirst()
                .orElse(null);

        if (nextPending != null) {
            // 이전 step 타입 조회
            String prevStepType = getPrevStepType(steps, nextPending.getStepOrder());

            // T-02: 마지막 REVIEW step 완료 후 첫 APPROVAL step 진입
            if ("REVIEW".equals(prevStepType) && "APPROVAL".equals(nextPending.getStepType())) {
                DocumentVersion version = documentVersionRepo.findById(wf.getVersionId())
                        .orElseThrow(() -> new NotFoundException("버전을 찾을 수 없음: " + wf.getVersionId()));
                String prevState = version.getState();
                version.setState("UNDER_APPROVAL");
                documentVersionRepo.save(version);
                auditService.log(AuditEvent.of("SYSTEM", AuditAction.STATE_TRANSITION)
                        .entity("document_version", String.valueOf(wf.getVersionId()))
                        .before("\"" + prevState + "\"")
                        .after("\"UNDER_APPROVAL\"")
                        .build());
            }

            nextPending.setState("IN_PROGRESS");
            nextPending.setStartedAt(OffsetDateTime.now());
            wf.setCurrentStep(nextPending.getStepOrder());
            wfStepRepo.save(nextPending);
            wfInstanceRepo.save(wf);
        } else {
            // 모든 step COMPLETED → T-03
            applyEffectiveTransition(wf, steps);
        }
    }

    private String getPrevStepType(List<WorkflowStepInstance> steps, int currentOrder) {
        return steps.stream()
                .filter(s -> s.getStepOrder() < currentOrder)
                .max(java.util.Comparator.comparingInt(WorkflowStepInstance::getStepOrder))
                .map(WorkflowStepInstance::getStepType)
                .orElse(null);
    }

    private void applyEffectiveTransition(WorkflowInstance wf, List<WorkflowStepInstance> steps) {
        DocumentVersion version = documentVersionRepo.findById(wf.getVersionId())
                .orElseThrow(() -> new NotFoundException("버전을 찾을 수 없음: " + wf.getVersionId()));
        Document document = documentRepo.findById(version.getDocumentId())
                .orElseThrow(() -> new NotFoundException("문서를 찾을 수 없음: " + version.getDocumentId()));
        DocumentCategory category = categoryRepo.findById(document.getCategoryId())
                .orElseThrow(() -> new NotFoundException("카테고리를 찾을 수 없음: " + document.getCategoryId()));

        // qa_mandatory 런타임 가드 (D11)
        if (category.isQaMandatory()) {
            boolean hasQaCompleted = steps.stream()
                    .anyMatch(s -> s.isQaRequired() && "COMPLETED".equals(s.getState()));
            if (!hasQaCompleted) {
                throw new UnprocessableEntityException("WORKFLOW_020",
                        "qa_mandatory 카테고리는 QA 승인 단계 통과가 필요합니다");
            }
        }

        // SELECT FOR UPDATE
        documentRepo.lockForUpdate(document.getId());

        // T-07: UNDER_REVISION 상태 버전 → SUPERSEDED
        documentVersionRepo.findByDocumentIdAndState(document.getId(), "UNDER_REVISION")
                .ifPresent(old -> {
                    if (!old.getId().equals(wf.getVersionId())) {
                        String oldPrevState = old.getState();
                        old.setState("SUPERSEDED");
                        documentVersionRepo.save(old);
                        auditService.log(AuditEvent.of("SYSTEM", AuditAction.STATE_TRANSITION)
                                .entity("document_version", String.valueOf(old.getId()))
                                .before("\"" + oldPrevState + "\"")
                                .after("\"SUPERSEDED\"")
                                .build());
                        auditService.log(AuditEvent.of("SYSTEM", AuditAction.DOCUMENT_VERSION_SUPERSEDED)
                                .entity("document_version", String.valueOf(old.getId()))
                                .build());
                    }
                });

        // revision 채번
        int newRev = documentVersionRepo.findMaxRevisionByDocumentId(document.getId())
                .map(r -> r + 1).orElse(0);
        String prevVersionState = version.getState();
        version.setRevision(newRev);
        version.setEffectiveDate(LocalDate.now(ZoneId.of("Asia/Seoul")));
        version.setState("EFFECTIVE");
        documentVersionRepo.save(version);

        wf.setState("COMPLETED");
        wf.setCompletedAt(OffsetDateTime.now());
        wfInstanceRepo.save(wf);

        auditService.log(AuditEvent.of("SYSTEM", AuditAction.STATE_TRANSITION)
                .entity("document_version", String.valueOf(wf.getVersionId()))
                .before("\"" + prevVersionState + "\"")
                .after("\"EFFECTIVE\"")
                .build());
        auditService.log(AuditEvent.of("SYSTEM", AuditAction.DOCUMENT_VERSION_EFFECTIVE)
                .entity("document_version", String.valueOf(wf.getVersionId()))
                .build());
        auditService.log(AuditEvent.of("SYSTEM", AuditAction.WORKFLOW_COMPLETED)
                .entity("workflow_instance", String.valueOf(wf.getId()))
                .build());
    }

    /**
     * GET /workflow/my-pending 응답용 목록 조회
     */
    public List<PendingTaskDto> getMyPending(String userIdString) {
        List<WorkflowStepInstance> steps = wfStepRepo.findMyPending(userIdString);
        return steps.stream().map(step -> {
            WorkflowInstance wf = wfInstanceRepo.findById(step.getWorkflowId())
                    .orElseThrow(() -> new NotFoundException("워크플로를 찾을 수 없음: " + step.getWorkflowId()));
            DocumentVersion version = documentVersionRepo.findById(wf.getVersionId())
                    .orElseThrow(() -> new NotFoundException("버전을 찾을 수 없음: " + wf.getVersionId()));
            Document doc = documentRepo.findById(version.getDocumentId())
                    .orElseThrow(() -> new NotFoundException("문서를 찾을 수 없음: " + version.getDocumentId()));

            Instant assignedAt = step.getAssignees().stream()
                    .filter(a -> userIdString.equals(a.userIdString()))
                    .map(AssigneeRef::fixedAt)
                    .findFirst().orElse(Instant.now());

            return new PendingTaskDto(
                    doc.getId(), doc.getDocNumber(), version.getTitle(),
                    wf.getVersionId(), version.getState(),
                    wf.getId(), step.getId(),
                    step.getStepOrder(), step.getStepType(), step.getRoleCode(),
                    assignedAt
            );
        }).toList();
    }

    private User getUserByUserId(String userId) {
        return userRepo.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없음: " + userId));
    }

    private boolean verifyPassword(String userId, String rawPassword) {
        User user = getUserByUserId(userId);
        if (user.getPasswordHash() == null) return false;
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }
}
