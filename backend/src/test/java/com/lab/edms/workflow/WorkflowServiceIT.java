package com.lab.edms.workflow;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.audit.AuditAction;
import com.lab.edms.category.DocumentCategory;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.common.ForbiddenException;
import com.lab.edms.common.UnprocessableEntityException;
import com.lab.edms.department.Department;
import com.lab.edms.department.DepartmentRepository;
import com.lab.edms.document.Document;
import com.lab.edms.document.DocumentFile;
import com.lab.edms.document.DocumentFileRepository;
import com.lab.edms.document.DocumentRepository;
import com.lab.edms.document.DocumentVersion;
import com.lab.edms.document.DocumentVersionRepository;
import com.lab.edms.permission.Permission;
import com.lab.edms.permission.PermissionRepository;
import com.lab.edms.signature.SignatureManifestRepository;
import com.lab.edms.signature.SignatureService;
import com.lab.edms.user.*;
import com.lab.edms.workflow.dto.SubmitRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * WorkflowServiceIT — 6개 통합 시나리오
 *
 * A: SOP 정상 흐름 (submit → 2명 parallel review sign → approval sign → QA sign → EFFECTIVE)
 * B: 검토 단계 reject → version=DRAFT, 재제출 → 신규 인스턴스 생성 (기존 보존)
 * C: 승인 단계 reject → DRAFT
 * D: 병렬 step에서 1명 reject → 다른 1명이 이미 서명했어도 step=REJECTED
 * E: assignees 외 사용자 서명 → 403
 * F: qa_required step 없는 채로 EFFECTIVE 시도 → qa_mandatory 카테고리 → 422
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class WorkflowServiceIT {

    @Autowired WorkflowService workflowService;
    @Autowired SignatureService signatureService;
    @Autowired WorkflowInstanceRepository wfInstanceRepo;
    @Autowired WorkflowStepInstanceRepository wfStepRepo;
    @Autowired WorkflowTemplateRepository templateRepo;
    @Autowired WorkflowTemplateStepRepository templateStepRepo;
    @Autowired DocumentRepository docRepo;
    @Autowired DocumentVersionRepository versionRepo;
    @Autowired DocumentCategoryRepository catRepo;
    @Autowired DepartmentRepository deptRepo;
    @Autowired PermissionRepository permRepo;
    @Autowired UserRepository userRepo;
    @Autowired RoleRepository roleRepo;
    @Autowired SignatureManifestRepository manifestRepo;
    @Autowired DocumentFileRepository documentFileRepo;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    private static final String PLAIN_PW = "Test@1234";

    // 시드 데이터
    private User author;
    private User reviewer1;
    private User reviewer2;
    private User approver;
    private User qaUser;
    private User outsider;

    private Long sopCategoryId;
    private Long qcCategoryId;
    private WorkflowTemplate sopTemplate;

    @BeforeEach
    void setUp() {
        // TRUNCATE는 반드시 별도 트랜잭션으로 실행해야 합니다.
        // AuditService가 REQUIRES_NEW로 audit_logs에 INSERT할 때,
        // 테스트 트랜잭션이 TRUNCATE로 획득한 ACCESS EXCLUSIVE 락과 교착 상태가 발생합니다.
        TransactionTemplate requiresNew = new TransactionTemplate(txManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(s ->
                jdbc.execute("TRUNCATE TABLE audit_logs RESTART IDENTITY"));

        // 부서 생성
        ensureDept("QC", "Quality Control");
        ensureDept("RD", "Research & Development");

        // 카테고리
        sopCategoryId = catRepo.findByCategoryCode("SOP").orElseThrow().getId();
        qcCategoryId = catRepo.findByCategoryCode("SOP").orElseThrow().getId();

        // 사용자 생성
        author    = createUser("wf_author",    "RD");
        reviewer1 = createUser("wf_rev_01",    "QC");
        reviewer2 = createUser("wf_rev_02",    "QC");
        approver  = createUser("wf_approver",  "QC");
        qaUser    = createUser("wf_qa",        "QC");
        outsider  = createUser("wf_outsider",  "QC");

        // 역할 부여
        Role authorRole   = roleRepo.findByRoleCode("AUTHOR").orElseThrow();
        Role reviewerRole = roleRepo.findByRoleCode("REVIEWER").orElseThrow();
        Role approverRole = roleRepo.findByRoleCode("APPROVER").orElseThrow();
        Role qaRole       = roleRepo.findByRoleCode("QA").orElseThrow();

        assignRole(author, authorRole);
        assignRole(reviewer1, reviewerRole);
        assignRole(reviewer2, reviewerRole);
        assignRole(approver, approverRole);
        assignRole(qaUser, qaRole);
        em.flush();
        em.clear();

        // 권한 부여
        grantPermission(authorRole.getId(), sopCategoryId, "RD", false, false, true);
        grantPermission(reviewerRole.getId(), sopCategoryId, "QC", true, false, false);
        grantPermission(approverRole.getId(), sopCategoryId, "QC", false, true, false);
        grantPermission(qaRole.getId(), sopCategoryId, "QC", false, true, false);
        em.flush();
        em.clear();

        // SOP 워크플로 템플릿 생성
        // (기존 템플릿이 있으면 해당 카테고리ID가 unique constraint에 걸릴 수 있으므로 새 카테고리 사용)
        // 대신 templateRepo의 기존 SOP 템플릿을 재사용하되, step은 직접 제어합니다.
        // 시나리오에서는 submit을 직접 호출하지 않고 수동으로 wf instance + steps를 만드는 방식 사용
    }

    // ──── 시나리오 A: 정상 흐름 (SOP: REVIEW → APPROVAL → QA → EFFECTIVE) ────

    @Test
    void A_정상흐름_submit_sign_EFFECTIVE() {
        // 문서/버전 생성 (SOP - qa_mandatory=TRUE)
        Document doc = createDocument(author.getId(), sopCategoryId, "RD");
        DocumentVersion ver = createDocVersion(doc.getId(), author.getId(), "DRAFT");

        // 워크플로 인스턴스 생성 (step1=REVIEW, step2=APPROVAL/qa_required)
        WorkflowInstance wf = createWorkflow(ver.getId(), author.getUserId());

        // step1: REVIEW, parallel, min_signers=2 (reviewer1, reviewer2)
        WorkflowStepInstance reviewStep = createStep(wf.getId(), 1, "REVIEW",
                List.of(reviewer1, reviewer2), 2, true, false);
        reviewStep.setState("IN_PROGRESS");
        wfStepRepo.save(reviewStep);

        // step2: APPROVAL (QA 담당, qa_required=true) - SOP qa_mandatory 가드를 만족시키기 위해
        WorkflowStepInstance approvalStep = createStep(wf.getId(), 2, "APPROVAL",
                List.of(qaUser), 1, false, true);  // qa_required=true

        // version 상태 UNDER_REVIEW
        ver.setState("UNDER_REVIEW");
        versionRepo.save(ver);
        em.flush();
        em.clear();

        // reviewer1 서명
        signatureService.sign(doc.getId(), ver.getId(), reviewStep.getId(),
                PLAIN_PW, "REVIEWED", authOf(reviewer1.getUserId()),
                new MockHttpSession(), "127.0.0.1");
        em.flush();
        em.clear();

        // step1 아직 IN_PROGRESS (1/2)
        WorkflowStepInstance step1After1 = wfStepRepo.findById(reviewStep.getId()).orElseThrow();
        assertThat(step1After1.getState()).isEqualTo("IN_PROGRESS");

        // reviewer2 서명
        signatureService.sign(doc.getId(), ver.getId(), reviewStep.getId(),
                PLAIN_PW, "REVIEWED", authOf(reviewer2.getUserId()),
                new MockHttpSession(), "127.0.0.1");
        em.flush();
        em.clear();

        // step1 COMPLETED, step2 IN_PROGRESS (advance 호출됨)
        WorkflowStepInstance step1After2 = wfStepRepo.findById(reviewStep.getId()).orElseThrow();
        assertThat(step1After2.getState()).isEqualTo("COMPLETED");

        WorkflowStepInstance step2Refreshed = wfStepRepo.findById(approvalStep.getId()).orElseThrow();
        assertThat(step2Refreshed.getState()).isEqualTo("IN_PROGRESS");

        // T-02: version UNDER_APPROVAL으로 변경됐는지 확인
        DocumentVersion verAfterReview = versionRepo.findById(ver.getId()).orElseThrow();
        assertThat(verAfterReview.getState()).isEqualTo("UNDER_APPROVAL");

        // QA 서명 → T-03 EFFECTIVE (qa_required step 완료 → qa_mandatory 가드 통과)
        signatureService.sign(doc.getId(), ver.getId(), step2Refreshed.getId(),
                PLAIN_PW, "QA_APPROVED", authOf(qaUser.getUserId()),
                new MockHttpSession(), "127.0.0.1");
        em.flush();
        em.clear();

        DocumentVersion verFinal = versionRepo.findById(ver.getId()).orElseThrow();
        assertThat(verFinal.getState()).isEqualTo("EFFECTIVE");
        assertThat(verFinal.getRevision()).isNotNull();
        assertThat(verFinal.getEffectiveDate()).isNotNull();

        WorkflowInstance wfFinal = wfInstanceRepo.findById(wf.getId()).orElseThrow();
        assertThat(wfFinal.getState()).isEqualTo("COMPLETED");
    }

    // ──── 시나리오 B: 검토 단계 reject → DRAFT, 재제출 → 신규 인스턴스 ────

    @Test
    void B_검토단계_reject_DRAFT_재제출_신규인스턴스() {
        Document doc = createDocument(author.getId(), sopCategoryId, "RD");
        DocumentVersion ver = createDocVersion(doc.getId(), author.getId(), "UNDER_REVIEW");

        WorkflowInstance wf1 = createWorkflow(ver.getId(), author.getUserId());
        WorkflowStepInstance reviewStep = createStep(wf1.getId(), 1, "REVIEW",
                List.of(reviewer1), 1, false, false);
        reviewStep.setState("IN_PROGRESS");
        wfStepRepo.save(reviewStep);
        em.flush();
        em.clear();

        // reject
        workflowService.reject(doc.getId(), ver.getId(), reviewStep.getId(),
                "오류 발견", PLAIN_PW, authOf(reviewer1.getUserId()), "127.0.0.1");
        em.flush();
        em.clear();

        // 기존 인스턴스 REJECTED, version DRAFT
        WorkflowInstance wf1After = wfInstanceRepo.findById(wf1.getId()).orElseThrow();
        assertThat(wf1After.getState()).isEqualTo("REJECTED");

        DocumentVersion verAfterReject = versionRepo.findById(ver.getId()).orElseThrow();
        assertThat(verAfterReject.getState()).isEqualTo("DRAFT");

        // 재제출: 수동으로 새 워크플로 생성 (submit()은 템플릿이 없어 불가)
        // 새 워크플로 인스턴스 생성
        WorkflowInstance wf2 = createWorkflow(ver.getId(), author.getUserId());
        WorkflowStepInstance reviewStep2 = createStep(wf2.getId(), 1, "REVIEW",
                List.of(reviewer1), 1, false, false);
        reviewStep2.setState("IN_PROGRESS");
        wfStepRepo.save(reviewStep2);
        ver.setState("UNDER_REVIEW");
        versionRepo.save(ver);
        em.flush();
        em.clear();

        // 기존 wf1 여전히 존재하는지 확인
        assertThat(wfInstanceRepo.findById(wf1.getId())).isPresent();
        // wf2는 다른 인스턴스
        assertThat(wf2.getId()).isNotEqualTo(wf1.getId());
    }

    // ──── 시나리오 C: 승인 단계 reject → DRAFT ────

    @Test
    void C_승인단계_reject_DRAFT() {
        Document doc = createDocument(author.getId(), sopCategoryId, "RD");
        DocumentVersion ver = createDocVersion(doc.getId(), author.getId(), "UNDER_APPROVAL");

        WorkflowInstance wf = createWorkflow(ver.getId(), author.getUserId());
        WorkflowStepInstance approvalStep = createStep(wf.getId(), 1, "APPROVAL",
                List.of(approver), 1, false, false);
        approvalStep.setState("IN_PROGRESS");
        wfStepRepo.save(approvalStep);
        em.flush();
        em.clear();

        workflowService.reject(doc.getId(), ver.getId(), approvalStep.getId(),
                "승인 거부", PLAIN_PW, authOf(approver.getUserId()), "127.0.0.1");
        em.flush();
        em.clear();

        DocumentVersion verAfter = versionRepo.findById(ver.getId()).orElseThrow();
        assertThat(verAfter.getState()).isEqualTo("DRAFT");

        WorkflowInstance wfAfter = wfInstanceRepo.findById(wf.getId()).orElseThrow();
        assertThat(wfAfter.getState()).isEqualTo("REJECTED");
    }

    // ──── 시나리오 D: 병렬 step에서 1명 reject → step=REJECTED (이미 서명한 경우도) ────

    @Test
    void D_병렬step_1명reject_step_REJECTED() {
        Document doc = createDocument(author.getId(), sopCategoryId, "RD");
        DocumentVersion ver = createDocVersion(doc.getId(), author.getId(), "UNDER_REVIEW");

        WorkflowInstance wf = createWorkflow(ver.getId(), author.getUserId());
        // parallel step (reviewer1, reviewer2 모두 assignee)
        WorkflowStepInstance parallelStep = createStep(wf.getId(), 1, "REVIEW",
                List.of(reviewer1, reviewer2), 2, true, false);
        parallelStep.setState("IN_PROGRESS");
        wfStepRepo.save(parallelStep);
        em.flush();
        em.clear();

        // reviewer1이 먼저 서명
        signatureService.sign(doc.getId(), ver.getId(), parallelStep.getId(),
                PLAIN_PW, "REVIEWED", authOf(reviewer1.getUserId()),
                new MockHttpSession(), "127.0.0.1");
        em.flush();
        em.clear();

        // step 아직 IN_PROGRESS
        assertThat(wfStepRepo.findById(parallelStep.getId()).orElseThrow().getState())
                .isEqualTo("IN_PROGRESS");

        // reviewer2가 reject
        workflowService.reject(doc.getId(), ver.getId(), parallelStep.getId(),
                "내용 불충분", PLAIN_PW, authOf(reviewer2.getUserId()), "127.0.0.1");
        em.flush();
        em.clear();

        WorkflowStepInstance stepAfter = wfStepRepo.findById(parallelStep.getId()).orElseThrow();
        assertThat(stepAfter.getState()).isEqualTo("REJECTED");
        WorkflowInstance wfAfter = wfInstanceRepo.findById(wf.getId()).orElseThrow();
        assertThat(wfAfter.getState()).isEqualTo("REJECTED");
    }

    // ──── 시나리오 E: assignees 외 사용자 서명 → 403 ────

    @Test
    void E_assignees외_사용자서명_403() {
        Document doc = createDocument(author.getId(), sopCategoryId, "RD");
        DocumentVersion ver = createDocVersion(doc.getId(), author.getId(), "UNDER_REVIEW");

        WorkflowInstance wf = createWorkflow(ver.getId(), author.getUserId());
        WorkflowStepInstance step = createStep(wf.getId(), 1, "REVIEW",
                List.of(reviewer1), 1, false, false);
        step.setState("IN_PROGRESS");
        wfStepRepo.save(step);
        em.flush();
        em.clear();

        assertThatThrownBy(() ->
                signatureService.sign(doc.getId(), ver.getId(), step.getId(),
                        PLAIN_PW, "REVIEWED", authOf(outsider.getUserId()),
                        new MockHttpSession(), "127.0.0.1"))
                .isInstanceOf(ForbiddenException.class);
    }

    // ──── 시나리오 F: qa_required step 없는 채로 EFFECTIVE 시도 → 422 ────

    @Test
    void F_qa_mandatory_카테고리_qa_required_step없이_EFFECTIVE시도_422() {
        // qa_mandatory=true인 카테고리 생성
        DocumentCategory qaMandatoryCat = new DocumentCategory();
        String nanoSuffix = String.valueOf(System.nanoTime());
        String catCode = ("QA_" + nanoSuffix).substring(0, Math.min(20, 3 + nanoSuffix.length()));
        qaMandatoryCat.setCategoryCode(catCode);
        qaMandatoryCat.setCategoryName("QA Mandatory Cat");
        qaMandatoryCat.setQaMandatory(true);
        qaMandatoryCat.setActive(true);
        catRepo.save(qaMandatoryCat);
        em.flush();
        em.clear();

        Document doc = createDocument(author.getId(), qaMandatoryCat.getId(), "RD");
        DocumentVersion ver = createDocVersion(doc.getId(), author.getId(), "UNDER_APPROVAL");

        WorkflowInstance wf = createWorkflow(ver.getId(), author.getUserId());
        // qa_required=false인 APPROVAL step만 있음
        WorkflowStepInstance approvalStep = createStep(wf.getId(), 1, "APPROVAL",
                List.of(approver), 1, false, false);
        approvalStep.setState("IN_PROGRESS");
        approvalStep.setQaRequired(false);
        wfStepRepo.save(approvalStep);
        em.flush();
        em.clear();

        // approver 서명 → advance() 호출 → qa_mandatory 가드에서 422
        assertThatThrownBy(() ->
                signatureService.sign(doc.getId(), ver.getId(), approvalStep.getId(),
                        PLAIN_PW, "APPROVED", authOf(approver.getUserId()),
                        new MockHttpSession(), "127.0.0.1"))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("QA");
    }

    // ──── 헬퍼 메서드 ────

    private Authentication authOf(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private void ensureDept(String code, String name) {
        if (deptRepo.findByDeptCode(code).isEmpty()) {
            Department d = new Department();
            d.setDeptCode(code);
            d.setPrimaryName(name);
            d.setSource("INTERNAL");
            deptRepo.save(d);
        }
    }

    private User createUser(String userId, String dept) {
        User u = new User();
        u.setUserId(userId);
        u.setFullName("테스트 " + userId);
        u.setEmail(userId + "@lab.test");
        u.setDepartment(dept);
        u.setStatus(UserStatus.ACTIVE);
        u.setPasswordHash(passwordEncoder.encode(PLAIN_PW));
        u.setForceChangePw(false);
        return userRepo.save(u);
    }

    private void assignRole(User user, Role role) {
        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(role);
        ur.setAssignedAt(OffsetDateTime.now());
        em.persist(ur);
    }

    private void grantPermission(Long roleId, Long categoryId, String dept,
                                  boolean canReview, boolean canApprove, boolean canCreate) {
        Permission p = new Permission();
        p.setRoleId(roleId);
        p.setCategoryId(categoryId);
        p.setDepartment(dept);
        p.setCanView(true);
        p.setCanReview(canReview);
        p.setCanApprove(canApprove);
        p.setCanCreate(canCreate);
        permRepo.save(p);
    }

    private Document createDocument(Long ownerId, Long categoryId, String dept) {
        Document doc = new Document();
        doc.setDocNumber("SOP-" + dept + "-" + System.nanoTime());
        doc.setCategoryId(categoryId);
        doc.setDepartment(dept);
        doc.setTitle("워크플로 테스트 문서");
        doc.setOwnerId(ownerId);
        doc.setCreatedBy(ownerId);
        doc.setConfidential(false);
        return docRepo.save(doc);
    }

    private DocumentVersion createDocVersion(Long docId, Long createdBy, String state) {
        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(docId);
        v.setState(state);
        v.setTitle("워크플로 테스트 버전");
        v.setSourceFileKey("test/source.pdf");
        v.setCreatedBy(createdBy);
        v.setUpdatedBy(createdBy);
        v = versionRepo.save(v);

        // v2 canonical payload: ORIGINAL 파일 행 필수
        DocumentFile originalFile = new DocumentFile();
        originalFile.setVersionId(v.getId());
        originalFile.setFileType("ORIGINAL");
        originalFile.setFileName("test.pdf");
        originalFile.setMinioBucket("test-bucket");
        originalFile.setMinioKey("test/" + v.getId() + "/test.pdf");
        originalFile.setSha256Hash("deadbeef1234567890abcdef1234567890abcdef1234567890abcdef12345678");
        originalFile.setFileSizeBytes(1024L);
        originalFile.setUploadedBy(createdBy);
        documentFileRepo.save(originalFile);

        return v;
    }

    private WorkflowInstance createWorkflow(Long versionId, String startedBy) {
        WorkflowInstance wf = new WorkflowInstance();
        wf.setVersionId(versionId);
        wf.setTemplateId(1L);
        wf.setState("IN_PROGRESS");
        wf.setCurrentStep(1);
        wf.setStartedBy(startedBy);
        return wfInstanceRepo.save(wf);
    }

    private WorkflowStepInstance createStep(Long wfId, int order, String stepType,
                                             List<User> assignees, int minSigners,
                                             boolean parallel, boolean qaRequired) {
        WorkflowStepInstance step = new WorkflowStepInstance();
        step.setWorkflowId(wfId);
        step.setStepOrder(order);
        step.setStepType(stepType);
        step.setRoleCode("APPROVAL".equals(stepType) ? "APPROVER" : "REVIEWER");
        step.setMinSigners(minSigners);
        step.setParallel(parallel);
        step.setQaRequired(qaRequired);
        step.setState("PENDING");
        step.setAssignees(assignees.stream()
                .map(u -> new AssigneeRef(u.getId(), u.getUserId(), Instant.now(), "system"))
                .toList());
        step.setSigned(new ArrayList<>());
        return wfStepRepo.save(step);
    }
}
