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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * SignatureLockoutRollbackIT
 *
 * REQUIRES_NEW 커밋 불변식 검증:
 * sign()은 @Transactional이므로 UnauthorizedException 발생 시 롤백된다.
 * 그러나 recordFailureInNewTransaction()은 @Transactional(propagation=REQUIRES_NEW)로
 * 별도 트랜잭션에서 커밋되므, User.failedAttempts 증가는 반드시 영속되어야 한다.
 *
 * 시나리오:
 * 1. 잘못된 PW 1회 → failedAttempts == 1 (REQUIRES_NEW 커밋, main tx 롤백에도 영속)
 * 2. 잘못된 PW 5회 연속 → failedAttempts >= 5, status == LOCKED
 */
/**
 * 주의: 이 테스트 클래스는 의도적으로 @Transactional을 사용하지 않는다.
 * REQUIRES_NEW 불변식을 검증하려면 테스트 트랜잭션이 없어야 한다:
 *   - @Transactional 테스트에서 JdbcTemplate은 동일 트랜잭션에 참여하므로
 *     REQUIRES_NEW로 커밋된 변경이 읽히지 않는다.
 * 대신 @AfterEach에서 테스트 데이터를 직접 정리한다.
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
class SignatureLockoutRollbackIT {

    private static final String TEST_SHA256 =
            "deadbeef1234567890abcdef1234567890abcdef1234567890abcdef12345678";

    @Autowired SignatureService signatureService;
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

    private static final String PLAIN_PASSWORD = "Test@1234";

    private User testUser;
    private Document document;
    private DocumentVersion docVersion;
    private WorkflowInstance wfInstance;
    private WorkflowStepInstance stepInstance;
    private Long sopCategoryId;

    @AfterEach
    void tearDown() {
        // REQUIRES_NEW 불변식 검증을 위해 @Transactional 없이 실행하므로
        // 테스트 데이터를 직접 정리한다.
        jdbc.execute("DELETE FROM signature_manifests");
        jdbc.execute("DELETE FROM workflow_step_instances");
        jdbc.execute("DELETE FROM workflow_instances");
        jdbc.execute("DELETE FROM document_files");
        jdbc.execute("DELETE FROM document_versions");
        jdbc.execute("DELETE FROM documents");
        jdbc.execute("DELETE FROM permissions");
        jdbc.execute("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE user_id LIKE 'lockout_test_%')");
        jdbc.execute("DELETE FROM users WHERE user_id LIKE 'lockout_test_%'");
        jdbc.execute("DELETE FROM audit_logs");
    }

    @BeforeEach
    void setUp() {
        // @Transactional 없이 실행되므로 TransactionTemplate으로 setUp을 명시적 트랜잭션 내에서 실행
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

            testUser = createUser("lockout_test_01", PLAIN_PASSWORD);

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
     * 잘못된 PW 1회 → failedAttempts == 1
     *
     * sign()의 main 트랜잭션은 UnauthorizedException으로 롤백되지만,
     * recordFailureInNewTransaction()이 REQUIRES_NEW로 커밋했으므로
     * failedAttempts 증가는 반드시 DB에 영속되어야 한다.
     *
     * @Transactional 테스트 클래스의 L1 캐시를 우회하기 위해 JdbcTemplate으로 직접 조회한다.
     */
    @Test
    void pwFailure_incrementsFailedAttempts_despiteMainTransactionRollback() {
        Authentication auth = authOf(testUser.getUserId());
        MockHttpSession session = new MockHttpSession();

        assertThatThrownBy(() ->
                signatureService.sign(
                        document.getId(), docVersion.getId(), stepInstance.getId(),
                        "wrong_password_xyz", "REVIEWED", auth, session, "127.0.0.1"))
                .isInstanceOf(UnauthorizedException.class);

        // REQUIRES_NEW 트랜잭션은 이미 커밋됨.
        // JdbcTemplate으로 L1 캐시를 우회하여 커밋된 값을 직접 읽는다.
        Integer attempts = jdbc.queryForObject(
                "SELECT failed_attempts FROM users WHERE user_id = ?",
                Integer.class, testUser.getUserId());

        assertThat(attempts).isEqualTo(1);
    }

    /**
     * 잘못된 PW 5회 연속 → failedAttempts >= 5, status == LOCKED
     *
     * MAX_FAILED_ATTEMPTS(=5) 도달 시 recordFailureInNewTransaction()이
     * status를 LOCKED로 변경하고 커밋한다.
     * main 트랜잭션 롤백과 무관하게 계정이 잠겨야 한다.
     */
    @Test
    void fiveConsecutivePwFailures_locksAccount() {
        Authentication auth = authOf(testUser.getUserId());

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() ->
                    signatureService.sign(
                            document.getId(), docVersion.getId(), stepInstance.getId(),
                            "wrong", "REVIEWED", auth, new MockHttpSession(), "127.0.0.1"))
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
        assertThat(status).isEqualTo(com.lab.edms.user.UserStatus.LOCKED.name());
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
        doc.setDocNumber("SOP-QC-LOCK-" + System.nanoTime());
        doc.setCategoryId(sopCategoryId);
        doc.setDepartment("QC");
        doc.setTitle("잠금 테스트 문서");
        doc.setOwnerId(testUser != null ? testUser.getId() : 1L);
        doc.setCreatedBy(testUser != null ? testUser.getId() : 1L);
        doc.setConfidential(false);
        return docRepo.save(doc);
    }

    private DocumentVersion createDocVersion(Long docId, Long createdBy) {
        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(docId);
        v.setState("UNDER_REVIEW");
        v.setTitle("잠금 테스트 버전");
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
