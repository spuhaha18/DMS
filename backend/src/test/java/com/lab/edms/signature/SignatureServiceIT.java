package com.lab.edms.signature;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.audit.AuditAction;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.common.ForbiddenException;
import com.lab.edms.common.UnauthorizedException;
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
import com.lab.edms.user.*;
import com.lab.edms.workflow.*;
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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * SignatureServiceIT — SignatureService.sign() 통합 테스트
 *
 * 시나리오:
 * 1. 정상 서명 → manifest INSERT, sessionFirst=true (첫 호출)
 * 2. 같은 세션 두 번째 서명 → sessionFirst=false
 * 3. 잘못된 PW → 401, audit USER_LOGIN_FAIL
 * 4. assignees에 없는 사용자 → 403
 * 5. step.state='COMPLETED' 후 재서명 → 422
 * 6. min_signers=2, parallel=true → 1차 서명 시 step IN_PROGRESS 유지, 2차 서명 시 COMPLETED
 * 7. (신규) 서로 다른 sha256 가진 버전 서명 → thisHash 상이
 * 8. (신규) ORIGINAL 파일 없음 → 422 SIGNATURE_001
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class SignatureServiceIT {

    // ──── genesis hash (SHA-256("GENESIS")) ────
    private static final String GENESIS_HASH;
    static {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest("GENESIS".getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            GENESIS_HASH = sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 테스트용 sha256 고정값
    private static final String TEST_SHA256 =
            "deadbeef1234567890abcdef1234567890abcdef1234567890abcdef12345678";
    private static final String TEST_SHA256_ALT =
            "aaaa0000000000000000000000000000000000000000000000000000bbbb1111";

    @Autowired SignatureService signatureService;
    @Autowired SignatureRateLimiter rateLimiter;
    @Autowired SignatureManifestRepository manifestRepo;
    @Autowired WorkflowStepInstanceRepository wfStepRepo;
    @Autowired WorkflowInstanceRepository wfInstanceRepo;
    @Autowired DocumentRepository docRepo;
    @Autowired DocumentVersionRepository versionRepo;
    @Autowired UserRepository userRepo;
    @Autowired RoleRepository roleRepo;
    @Autowired DocumentCategoryRepository catRepo;
    @Autowired DepartmentRepository deptRepo;
    @Autowired PermissionRepository permRepo;
    @Autowired DocumentFileRepository docFileRepo;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    private static final String PLAIN_PASSWORD = "Test@1234";

    // 시나리오별 공유 데이터
    private User reviewer1;
    private User reviewer2;
    private User outsider;
    private Document document;
    private DocumentVersion docVersion;
    private WorkflowInstance wfInstance;
    private WorkflowStepInstance stepInstance;
    private Long sopCategoryId;

    @BeforeEach
    void setUp() {
        rateLimiter.resetAll();
        // 감사 로그 초기화 (해시체인 시작점 초기화)
        // TRUNCATE는 AccessExclusiveLock을 획득하여 @Transactional 테스트 내에서 AuditService
        // SELECT와 deadlock을 유발함 → DELETE로 대체 (RowExclusiveLock 사용, lock 충돌 없음)
        jdbc.execute("DELETE FROM audit_logs");

        // 부서 생성
        if (deptRepo.findByDeptCode("QC").isEmpty()) {
            Department qc = new Department();
            qc.setDeptCode("QC");
            qc.setPrimaryName("Quality Control");
            qc.setSource("INTERNAL");
            deptRepo.save(qc);
        }

        sopCategoryId = catRepo.findByCategoryCode("FORM").orElseThrow().getId(); // qa_mandatory=FALSE

        // 사용자 생성
        reviewer1 = createUser("sig_rev_01", PLAIN_PASSWORD);
        reviewer2 = createUser("sig_rev_02", PLAIN_PASSWORD);
        outsider  = createUser("sig_outsider", PLAIN_PASSWORD);

        // 역할 부여
        Role reviewerRole = roleRepo.findByRoleCode("REVIEWER").orElseThrow();
        assignRole(reviewer1, reviewerRole);
        assignRole(reviewer2, reviewerRole);
        em.flush();
        em.clear();

        // 권한 부여
        grantPermission(reviewerRole.getId(), sopCategoryId, "QC", true, false);
        em.flush();
        em.clear();

        // 문서/버전 생성
        document = createDocument();
        docVersion = createDocVersion(document.getId(), reviewer1.getId());

        // ORIGINAL 파일 행 삽입 (sign()의 documentFileRepo 조회를 위해 필수)
        insertOriginalFile(docVersion.getId(), reviewer1.getId(), TEST_SHA256);
        em.flush();
        em.clear();

        // 워크플로 인스턴스 생성
        wfInstance = new WorkflowInstance();
        wfInstance.setVersionId(docVersion.getId());
        wfInstance.setTemplateId(1L);  // 임의 템플릿 ID
        wfInstance.setState("IN_PROGRESS");
        wfInstance.setCurrentStep(1);
        wfInstance.setStartedBy(reviewer1.getUserId());
        wfInstanceRepo.save(wfInstance);

        // step 인스턴스 생성 (min_signers=1, assignee=reviewer1) - IN_PROGRESS로 시작
        stepInstance = createStepInstance(wfInstance.getId(), 1,
                List.of(reviewer1), 1, false, "REVIEW");
        stepInstance.setState("IN_PROGRESS");
        wfStepRepo.save(stepInstance);
        em.flush();
        em.clear();
    }

    // ──── 시나리오 1: 정상 서명 (sessionFirst=true) ────

    @Test
    void 정상서명_manifest_insert_sessionFirst_true() {
        MockHttpSession session = new MockHttpSession();
        Authentication auth = authOf(reviewer1.getUserId());

        SignatureManifest manifest = signatureService.sign(
                document.getId(), docVersion.getId(), stepInstance.getId(),
                PLAIN_PASSWORD, "REVIEWED", reviewer1.getUserId(),
                auth, session, "127.0.0.1");

        assertThat(manifest.getId()).isNotNull();
        assertThat(manifest.getSignerUserId()).isEqualTo(reviewer1.getUserId());
        assertThat(manifest.isSessionFirst()).isTrue();
        // genesis hash는 SHA-256("GENESIS") 64자 hex
        assertThat(manifest.getPrevHash()).isEqualTo(GENESIS_HASH);
        assertThat(manifest.getPrevHash()).hasSize(64);
        assertThat(manifest.getThisHash()).hasSize(64);
        // v2 알고리즘 버전 확인
        assertThat(manifest.getAlgorithmVersion()).isEqualTo("v2");
        // canonical payload 형식 검증: signerId|meaning|...sha256 끝
        assertThat(manifest.getCanonicalPayload())
                .contains("|REVIEWED|")
                .endsWith("|" + TEST_SHA256);

        // signed 목록에 추가됐는지 확인
        em.flush();
        em.clear();
        WorkflowStepInstance updated = wfStepRepo.findById(stepInstance.getId()).orElseThrow();
        assertThat(updated.getSigned()).hasSize(1);
    }

    // ──── 시나리오 2: 같은 세션 두 번째 서명 → sessionFirst=false ────

    @Test
    void 같은세션_두번째서명_sessionFirst_false() {
        MockHttpSession session = new MockHttpSession();
        Authentication auth = authOf(reviewer1.getUserId());

        // 첫 서명 (step1)
        signatureService.sign(
                document.getId(), docVersion.getId(), stepInstance.getId(),
                PLAIN_PASSWORD, "REVIEWED", reviewer1.getUserId(),
                auth, session, "127.0.0.1");

        em.flush();
        em.clear();

        // 두 번째 서명 (다른 step — step2를 IN_PROGRESS로 직접 설정)
        // step1이 완료되면 advance()가 불릴 수 있으므로, 아예 새 wf/step에서 독립 시나리오로 구성
        WorkflowInstance wf2 = new WorkflowInstance();
        wf2.setVersionId(docVersion.getId());
        wf2.setTemplateId(1L);
        wf2.setState("IN_PROGRESS");
        wf2.setCurrentStep(1);
        wf2.setStartedBy(reviewer1.getUserId());
        wfInstanceRepo.save(wf2);

        WorkflowStepInstance step2 = createStepInstance(wf2.getId(), 1,
                List.of(reviewer1), 1, false, "REVIEW");
        step2.setState("IN_PROGRESS");
        wfStepRepo.save(step2);
        em.flush();
        em.clear();

        SignatureManifest manifest2 = signatureService.sign(
                document.getId(), docVersion.getId(), step2.getId(),
                PLAIN_PASSWORD, "REVIEWED", null,
                auth, session, "127.0.0.1");

        assertThat(manifest2.isSessionFirst()).isFalse();
    }

    // ──── 시나리오 3: 잘못된 PW → 401, audit USER_LOGIN_FAIL ────

    @Test
    void 잘못된_PW_401_예외() {
        MockHttpSession session = new MockHttpSession();
        Authentication auth = authOf(reviewer1.getUserId());

        assertThatThrownBy(() ->
                signatureService.sign(
                        document.getId(), docVersion.getId(), stepInstance.getId(),
                        "WRONG_PASSWORD", "REVIEWED", reviewer1.getUserId(),
                        auth, session, "127.0.0.1"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("비밀번호");

        // audit USER_LOGIN_FAIL 기록 확인
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'USER_LOGIN_FAIL'",
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    // ──── 시나리오 4: assignees에 없는 사용자 → 403 ────

    @Test
    void assignees에_없는_사용자_403() {
        MockHttpSession session = new MockHttpSession();
        Authentication auth = authOf(outsider.getUserId());

        // outsider에게도 비밀번호 설정은 되어 있지만 assignee가 아님
        assertThatThrownBy(() ->
                signatureService.sign(
                        document.getId(), docVersion.getId(), stepInstance.getId(),
                        PLAIN_PASSWORD, "REVIEWED", outsider.getUserId(),
                        auth, session, "127.0.0.1"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("결재 권한");
    }

    // ──── 시나리오 5: step.state='COMPLETED' 후 재서명 → 422 ────

    @Test
    void step_COMPLETED_후_재서명_422() {
        // step을 COMPLETED 상태로 변경
        WorkflowStepInstance step = wfStepRepo.findById(stepInstance.getId()).orElseThrow();
        step.setState("COMPLETED");
        wfStepRepo.save(step);
        em.flush();
        em.clear();

        MockHttpSession session = new MockHttpSession();
        Authentication auth = authOf(reviewer1.getUserId());

        assertThatThrownBy(() ->
                signatureService.sign(
                        document.getId(), docVersion.getId(), stepInstance.getId(),
                        PLAIN_PASSWORD, "REVIEWED", reviewer1.getUserId(),
                        auth, session, "127.0.0.1"))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("처리된 단계");
    }

    // ──── 시나리오 6: min_signers=2, parallel=true → 1차 IN_PROGRESS 유지, 2차 COMPLETED ────

    @Test
    void parallel_minSigners2_2차서명시_COMPLETED() {
        // setUp()의 docVersion은 이미 wfInstance와 연결돼 있어 uq_one_active_workflow_per_version 위반.
        // 별도 문서/버전을 생성하여 독립 workflow 구성.
        Document docP = createDocument();
        DocumentVersion docVerP = createDocVersion(docP.getId(), reviewer1.getId());
        insertOriginalFile(docVerP.getId(), reviewer1.getId(), TEST_SHA256);
        em.flush();
        em.clear();

        WorkflowInstance wfParallel = new WorkflowInstance();
        wfParallel.setVersionId(docVerP.getId());
        wfParallel.setTemplateId(1L);
        wfParallel.setState("IN_PROGRESS");
        wfParallel.setCurrentStep(1);
        wfParallel.setStartedBy(reviewer1.getUserId());
        wfInstanceRepo.save(wfParallel);

        // min_signers=2, parallel=true인 step (유일한 step)
        WorkflowStepInstance parallelStep = createStepInstance(wfParallel.getId(), 1,
                List.of(reviewer1, reviewer2), 2, true, "REVIEW");
        parallelStep.setState("IN_PROGRESS");
        wfStepRepo.save(parallelStep);
        em.flush();
        em.clear();

        // 1차 서명 (reviewer1)
        MockHttpSession session1 = new MockHttpSession();
        signatureService.sign(
                docP.getId(), docVerP.getId(), parallelStep.getId(),
                PLAIN_PASSWORD, "REVIEWED", reviewer1.getUserId(),
                authOf(reviewer1.getUserId()), session1, "127.0.0.1");

        em.flush();
        em.clear();
        WorkflowStepInstance afterFirst = wfStepRepo.findById(parallelStep.getId()).orElseThrow();
        assertThat(afterFirst.getState()).isEqualTo("IN_PROGRESS");  // 아직 1명 서명
        assertThat(afterFirst.getSigned()).hasSize(1);

        // 2차 서명 (reviewer2) → step COMPLETED, advance() → 다음 PENDING step 없음 → EFFECTIVE
        MockHttpSession session2 = new MockHttpSession();
        signatureService.sign(
                docP.getId(), docVerP.getId(), parallelStep.getId(),
                PLAIN_PASSWORD, "REVIEWED", reviewer2.getUserId(),
                authOf(reviewer2.getUserId()), session2, "127.0.0.1");

        em.flush();
        em.clear();
        WorkflowStepInstance afterSecond = wfStepRepo.findById(parallelStep.getId()).orElseThrow();
        assertThat(afterSecond.getState()).isEqualTo("COMPLETED");  // 2명 서명 완료
        assertThat(afterSecond.getSigned()).hasSize(2);
    }

    // ──── 시나리오 7 (신규): 서로 다른 sha256 가진 두 버전 → thisHash 상이 ────

    @Test
    void sign_differentSourceFileSha256_producesDistinctHash() {
        // 버전1은 setUp에서 생성 (TEST_SHA256) → reviewer1이 서명
        MockHttpSession session1 = new MockHttpSession();
        SignatureManifest manifest1 = signatureService.sign(
                document.getId(), docVersion.getId(), stepInstance.getId(),
                PLAIN_PASSWORD, "REVIEWED", reviewer1.getUserId(),
                authOf(reviewer1.getUserId()), session1, "127.0.0.1");
        String hash1 = manifest1.getThisHash();

        em.flush();
        em.clear();

        // 버전2: 다른 sha256으로 별도 문서버전 + workflow 생성
        Document doc2 = createDocument();
        DocumentVersion docVersion2 = createDocVersion(doc2.getId(), reviewer1.getId());
        // TEST_SHA256_ALT 로 ORIGINAL 파일 삽입
        insertOriginalFile(docVersion2.getId(), reviewer1.getId(), TEST_SHA256_ALT);
        em.flush();
        em.clear();

        WorkflowInstance wf2 = new WorkflowInstance();
        wf2.setVersionId(docVersion2.getId());
        wf2.setTemplateId(1L);
        wf2.setState("IN_PROGRESS");
        wf2.setCurrentStep(1);
        wf2.setStartedBy(reviewer1.getUserId());
        wfInstanceRepo.save(wf2);

        WorkflowStepInstance step2 = createStepInstance(wf2.getId(), 1,
                List.of(reviewer1), 1, false, "REVIEW");
        step2.setState("IN_PROGRESS");
        wfStepRepo.save(step2);
        em.flush();
        em.clear();

        MockHttpSession session2 = new MockHttpSession();
        SignatureManifest manifest2 = signatureService.sign(
                doc2.getId(), docVersion2.getId(), step2.getId(),
                PLAIN_PASSWORD, "REVIEWED", reviewer1.getUserId(),
                authOf(reviewer1.getUserId()), session2, "127.0.0.1");
        String hash2 = manifest2.getThisHash();

        // sha256이 다르면 canonical payload가 다르므로 thisHash도 달라야 함
        assertThat(hash1).isNotEqualTo(hash2);
        // 각 manifest의 canonical payload가 해당 sha256으로 끝나는지 확인
        assertThat(manifest1.getCanonicalPayload()).endsWith("|" + TEST_SHA256);
        assertThat(manifest2.getCanonicalPayload()).endsWith("|" + TEST_SHA256_ALT);
    }

    // ──── 시나리오 8 (신규): ORIGINAL 파일 없음 → 422 SIGNATURE_001 ────

    @Test
    void sign_missingOriginalFile_returns422() {
        // 새로운 버전을 생성하되 document_files 행을 삽입하지 않음
        Document doc3 = createDocument();
        DocumentVersion docVersion3 = createDocVersion(doc3.getId(), reviewer1.getId());
        // document_files 행 없음 (의도적)
        em.flush();
        em.clear();

        WorkflowInstance wf3 = new WorkflowInstance();
        wf3.setVersionId(docVersion3.getId());
        wf3.setTemplateId(1L);
        wf3.setState("IN_PROGRESS");
        wf3.setCurrentStep(1);
        wf3.setStartedBy(reviewer1.getUserId());
        wfInstanceRepo.save(wf3);

        WorkflowStepInstance step3 = createStepInstance(wf3.getId(), 1,
                List.of(reviewer1), 1, false, "REVIEW");
        step3.setState("IN_PROGRESS");
        wfStepRepo.save(step3);
        em.flush();
        em.clear();

        MockHttpSession session = new MockHttpSession();
        Authentication auth = authOf(reviewer1.getUserId());

        assertThatThrownBy(() ->
                signatureService.sign(
                        doc3.getId(), docVersion3.getId(), step3.getId(),
                        PLAIN_PASSWORD, "REVIEWED", reviewer1.getUserId(),
                        auth, session, "127.0.0.1"))
                .isInstanceOf(UnprocessableEntityException.class)
                .satisfies(ex -> assertThat(((UnprocessableEntityException) ex).getCode())
                        .isEqualTo("SIGNATURE_001"));
    }

    // ──── 헬퍼 메서드 ────

    private Authentication authOf(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private User createUser(String userId, String plainPassword) {
        User user = new User();
        user.setUserId(userId);
        user.setFullName("테스트 " + userId);
        user.setEmail(userId + "@lab.test");
        user.setDepartment("QC");
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        user.setForceChangePw(false);
        return userRepo.save(user);
    }

    private void assignRole(User user, Role role) {
        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(role);
        ur.setAssignedAt(OffsetDateTime.now());
        em.persist(ur);
    }

    private void grantPermission(Long roleId, Long categoryId, String dept,
                                  boolean canReview, boolean canApprove) {
        Permission p = new Permission();
        p.setRoleId(roleId);
        p.setCategoryId(categoryId);
        p.setDepartment(dept);
        p.setCanView(true);
        p.setCanReview(canReview);
        p.setCanApprove(canApprove);
        permRepo.save(p);
    }

    private Document createDocument() {
        Document doc = new Document();
        doc.setDocNumber("SOP-QC-IT-" + System.nanoTime());
        doc.setCategoryId(sopCategoryId);
        doc.setDepartment("QC");
        doc.setTitle("서명 테스트 문서");
        doc.setOwnerId(reviewer1 != null ? reviewer1.getId() : 1L);
        doc.setCreatedBy(reviewer1 != null ? reviewer1.getId() : 1L);
        doc.setConfidential(false);
        return docRepo.save(doc);
    }

    private DocumentVersion createDocVersion(Long docId, Long createdBy) {
        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(docId);
        v.setState("UNDER_REVIEW");
        v.setTitle("서명 테스트 버전");
        v.setSourceFileKey("test/source.pdf");
        v.setRevision(1);  // SignatureCanonicalSerializer.serialize()에서 int 언박싱 필요
        v.setCreatedBy(createdBy);
        v.setUpdatedBy(createdBy);
        return versionRepo.save(v);
    }

    /**
     * document_files ORIGINAL 행 삽입.
     * minio_bucket, minio_key, file_name, file_size_bytes, uploaded_by, sha256_hash 모두 NOT NULL.
     */
    private void insertOriginalFile(Long versionId, Long uploaderId, String sha256) {
        DocumentFile f = new DocumentFile();
        f.setVersionId(versionId);
        f.setFileType("ORIGINAL");
        f.setMinioBucket("test-bucket");
        f.setMinioKey("test/" + versionId + "/source.pdf");
        f.setFileName("test.pdf");
        f.setFileSizeBytes(1024L);
        f.setContentType("application/pdf");
        f.setSha256Hash(sha256);
        f.setUploadedBy(uploaderId);
        docFileRepo.save(f);
    }

    private WorkflowStepInstance createStepInstance(Long wfId, int order,
                                                     List<User> assignees, int minSigners,
                                                     boolean parallel, String stepType) {
        WorkflowStepInstance step = new WorkflowStepInstance();
        step.setWorkflowId(wfId);
        step.setStepOrder(order);
        step.setStepType(stepType);
        step.setRoleCode("REVIEWER");
        step.setMinSigners(minSigners);
        step.setParallel(parallel);
        step.setQaRequired(false);
        step.setState("PENDING");
        step.setAssignees(assignees.stream()
                .map(u -> new AssigneeRef(u.getId(), u.getUserId(), Instant.now(), "system"))
                .toList());
        step.setSigned(new ArrayList<>());
        return wfStepRepo.save(step);
    }
}
