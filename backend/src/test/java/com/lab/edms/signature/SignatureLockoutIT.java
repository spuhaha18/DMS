package com.lab.edms.signature;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.common.UnauthorizedException;
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
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * SignatureLockoutIT — OQ-SIG-008
 *
 * 전체 잠금 플로우 엔드-투-엔드 검증:
 * 1. 연속 5회 PW 실패 → User.status == LOCKED
 * 2. 계정 잠금 후 올바른 PW로 서명 시도 → 거부 (UnauthorizedException)
 *
 * 주의: @Transactional 없이 실행한다.
 * REQUIRES_NEW 커밋된 변경을 JdbcTemplate으로 직접 읽기 위함.
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
class SignatureLockoutIT {

    private static final String TEST_SHA256 =
            "deadbeef1234567890abcdef1234567890abcdef1234567890abcdef12345678";
    private static final String PLAIN_PASSWORD = "Test@1234";

    @Autowired SignatureService signatureService;
    @Autowired SignatureRateLimiter rateLimiter;
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
    @Autowired PlatformTransactionManager txManager;

    private User testUser;
    private Document document;
    private DocumentVersion docVersion;
    private WorkflowInstance wfInstance;
    private WorkflowStepInstance stepInstance;
    private Long sopCategoryId;

    @AfterEach
    void tearDown() {
        jdbc.execute("DELETE FROM signature_manifests");
        jdbc.execute("DELETE FROM sign_intents");
        jdbc.execute("DELETE FROM workflow_step_instances");
        jdbc.execute("DELETE FROM workflow_instances");
        jdbc.execute("DELETE FROM document_files");
        jdbc.execute("DELETE FROM document_versions");
        jdbc.execute("DELETE FROM documents");
        jdbc.execute("DELETE FROM permissions");
        jdbc.execute("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE user_id LIKE 'sig_lockout_%')");
        jdbc.execute("DELETE FROM users WHERE user_id LIKE 'sig_lockout_%'");
        jdbc.execute("DELETE FROM audit_logs");
    }

    @BeforeEach
    void setUp() {
        rateLimiter.resetAll();
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.execute(status -> {
            jdbc.execute("DELETE FROM audit_logs");

            if (deptRepo.findByDeptCode("QC").isEmpty()) {
                Department qc = new Department();
                qc.setDeptCode("QC");
                qc.setPrimaryName("Quality Control");
                qc.setSource("INTERNAL");
                deptRepo.save(qc);
            }

            sopCategoryId = catRepo.findByCategoryCode("FORM").orElseThrow().getId();

            testUser = createUser("sig_lockout_01", PLAIN_PASSWORD);

            Role reviewerRole = roleRepo.findByRoleCode("REVIEWER").orElseThrow();
            assignRole(testUser, reviewerRole);
            em.flush();
            em.clear();

            grantPermission(reviewerRole.getId(), sopCategoryId, "QC", true, false);
            em.flush();
            em.clear();

            document = createDocument();
            docVersion = createDocVersion(document.getId(), testUser.getId());
            insertOriginalFile(docVersion.getId(), testUser.getId(), TEST_SHA256);
            em.flush();
            em.clear();

            wfInstance = new WorkflowInstance();
            wfInstance.setVersionId(docVersion.getId());
            wfInstance.setTemplateId(1L);
            wfInstance.setState("IN_PROGRESS");
            wfInstance.setCurrentStep(1);
            wfInstance.setStartedBy(testUser.getUserId());
            wfInstanceRepo.save(wfInstance);

            stepInstance = createStepInstance(wfInstance.getId(), 1,
                    List.of(testUser), 1, false, "REVIEW");
            stepInstance.setState("IN_PROGRESS");
            wfStepRepo.save(stepInstance);
            em.flush();
            em.clear();

            return null;
        });
    }

    /**
     * OQ-SIG-008: 연속 5회 PW 실패 → User.status == LOCKED
     *
     * sign()의 main 트랜잭션은 매 호출마다 롤백되지만,
     * recordFailureInNewTransaction()이 REQUIRES_NEW로 커밋되므로
     * failedAttempts 카운터가 누적되어 5회째에 LOCKED 상태로 전환된다.
     */
    @Test
    void fiveConsecutivePwFailures_locksAccount() {
        Authentication auth = authOf(testUser.getUserId());

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() ->
                    signatureService.sign(
                            document.getId(), docVersion.getId(), stepInstance.getId(),
                            "WRONG_PASSWORD", "REVIEWED", testUser.getUserId(),
                            auth, new MockHttpSession(), "127.0.0.1"))
                    .isInstanceOf(UnauthorizedException.class);
        }

        // JdbcTemplate으로 L1 캐시 우회하여 커밋된 값 직접 조회
        Integer attempts = jdbc.queryForObject(
                "SELECT failed_attempts FROM users WHERE user_id = ?",
                Integer.class, testUser.getUserId());
        String status = jdbc.queryForObject(
                "SELECT status FROM users WHERE user_id = ?",
                String.class, testUser.getUserId());

        assertThat(attempts).isGreaterThanOrEqualTo(5);
        assertThat(status).isEqualTo(UserStatus.LOCKED.name());
    }

    /**
     * OQ-SIG-008: 잠금 계정에서 올바른 PW로 서명 시도 → 거부
     *
     * LOCKED 계정은 패스워드 일치 여부와 무관하게 인증이 거부되어야 한다.
     * (21 CFR Part 11 §11.300 — 전자 서명 구성 요소는 계정 상태와 연동)
     */
    @Test
    void lockedAccount_rejectsEvenCorrectPassword() {
        // 계정을 직접 LOCKED 상태로 설정
        jdbc.update(
                "UPDATE users SET failed_attempts = 5, status = 'LOCKED' WHERE user_id = ?",
                testUser.getUserId());

        Authentication auth = authOf(testUser.getUserId());

        // 올바른 패스워드로 서명 시도 → LOCKED이므로 거부
        assertThatThrownBy(() ->
                signatureService.sign(
                        document.getId(), docVersion.getId(), stepInstance.getId(),
                        PLAIN_PASSWORD, "REVIEWED", testUser.getUserId(),
                        auth, new MockHttpSession(), "127.0.0.1"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("잠겨");
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
        doc.setDocNumber("SOP-QC-SIGLOCK-" + System.nanoTime());
        doc.setCategoryId(sopCategoryId);
        doc.setDepartment("QC");
        doc.setTitle("서명 잠금 테스트 문서");
        doc.setOwnerId(testUser != null ? testUser.getId() : 1L);
        doc.setCreatedBy(testUser != null ? testUser.getId() : 1L);
        doc.setConfidential(false);
        return docRepo.save(doc);
    }

    private DocumentVersion createDocVersion(Long docId, Long createdBy) {
        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(docId);
        v.setState("UNDER_REVIEW");
        v.setTitle("서명 잠금 테스트 버전");
        v.setSourceFileKey("test/source.pdf");
        v.setRevision(1);
        v.setCreatedBy(createdBy);
        v.setUpdatedBy(createdBy);
        return versionRepo.save(v);
    }

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
