package com.lab.edms.workflow;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.common.ForbiddenException;
import com.lab.edms.common.NotFoundException;
import com.lab.edms.common.UnprocessableEntityException;
import com.lab.edms.document.Document;
import com.lab.edms.document.DocumentRepository;
import com.lab.edms.document.DocumentVersion;
import com.lab.edms.document.DocumentVersionRepository;
import com.lab.edms.lifecycle.LifecycleStateMachine;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.workflow.dto.SubmitRequest;
import com.lab.edms.workflow.dto.SubmitResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
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
    private final AssigneeResolver assigneeResolver;
    private final AuditService auditService;
    private final UserRepository userRepo;

    // LifecycleStateMachine은 직접 전이 문자열로 처리하므로 여기서는 상태 문자열 비교만 사용
    @SuppressWarnings("unused")
    private final LifecycleStateMachine stateMachine;

    public WorkflowService(WorkflowInstanceRepository wfInstanceRepo,
                           WorkflowStepInstanceRepository wfStepRepo,
                           WorkflowTemplateRepository templateRepo,
                           WorkflowTemplateStepRepository templateStepRepo,
                           DocumentRepository documentRepo,
                           DocumentVersionRepository documentVersionRepo,
                           AssigneeResolver assigneeResolver,
                           AuditService auditService,
                           UserRepository userRepo,
                           LifecycleStateMachine stateMachine) {
        this.wfInstanceRepo = wfInstanceRepo;
        this.wfStepRepo = wfStepRepo;
        this.templateRepo = templateRepo;
        this.templateStepRepo = templateStepRepo;
        this.documentRepo = documentRepo;
        this.documentVersionRepo = documentVersionRepo;
        this.assigneeResolver = assigneeResolver;
        this.auditService = auditService;
        this.userRepo = userRepo;
        this.stateMachine = stateMachine;
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

        // 5. 이미 진행 중인 워크플로 없는지 확인
        wfInstanceRepo.findActiveByVersion(verId).ifPresent(wi -> {
            throw new UnprocessableEntityException("WORKFLOW_002", "이미 진행 중인 결재가 있습니다");
        });

        // 6. SELECT FOR UPDATE
        documentRepo.lockForUpdate(docId);

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

    private User getUserByUserId(String userId) {
        return userRepo.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없음: " + userId));
    }
}
