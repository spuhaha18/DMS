package com.lab.edms.signature;

import com.lab.edms.TestcontainersConfig;
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
 * SignatureFirstSignIT — Part 11 §11.200(a) session-first signing 검증
 *
 * OQ-SIG-009: 새 세션 + signingUserId 제공 → 서명 성공, sessionFirst=true
 * OQ-SIG-010: 새 세션 + signingUserId 누락(null) → 422 SIGNATURE_002
 * OQ-SIG-010b: signingUserId 불일치 → 403 ForbiddenException
 * FLAG_IDEMPOTENCY: PW 실패 → 플래그 소비 안 됨 → 재시도 성공
 *
 * @Transactional 없이 실행: sign()의 REQUIRES_NEW 트랜잭션 동작과 플래그 불변식을
 * 정확하게 검증하기 위함. AfterEach에서 데이터 직접 정리.
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
class SignatureFirstSignIT {

    private static final String TEST_SHA256 =
            "deadbeef1234567890abcdef1234567890abcdef1234567890abcdef12345678";
    private static final String PLAIN_PASSWORD = "Test@1234";

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

    private User testUser;
    private Document document;
    private DocumentVersion docVersion;
    private WorkflowInstance wfInstance;
    private WorkflowStepInstance stepInstance;
    private Long sopCategoryId;

    @AfterEach
    void tearDown() {
        jdbc.execute("DELETE FROM signature_manifests");
        jdbc.execute("DELETE FROM workflow_step_instances");
        jdbc.execute("DELETE FROM workflow_instances");
        jdbc.execute("DELETE FROM document_files");
        jdbc.execute("DELETE FROM document_versions");
        jdbc.execute("DELETE FROM documents");
        jdbc.execute("DELETE FROM permissions");
        jdbc.execute("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE user_id LIKE 'first_sign_test_%')");
        jdbc.execute("DELETE FROM users WHERE user_id LIKE 'first_sign_test_%'");
        jdbc.execute("DELETE FROM audit_logs");
    }

    @BeforeEach
    void setUp() {
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

            testUser = createUser("first_sign_test_01", PLAIN_PASSWORD);

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

    // ===== OQ-SIG-009: 새 세션 + signingUserId 제공 → 서명 성공, sessionFirst=true =====
    @Test
    void oqSig009_sessionFirst_withUserId_succeeds() {
        MockHttpSession session = new MockHttpSession();
        Authentication auth = authOf(testUser.getUserId());

        SignatureManifest manifest = signatureService.sign(
                document.getId(), docVersion.getId(), stepInstance.getId(),
                PLAIN_PASSWORD, "REVIEWED",
                testUser.getUserId(),   // signingUserId 제공
                auth, session, "127.0.0.1");

        assertThat(manifest).isNotNull();
        assertThat(manifest.isSessionFirst()).isTrue();
    }

    // ===== OQ-SIG-010: 새 세션 + signingUserId null → 422 SIGNATURE_002 =====
    @Test
    void oqSig010_sessionFirst_withoutUserId_returns422() {
        MockHttpSession session = new MockHttpSession();
        Authentication auth = authOf(testUser.getUserId());

        assertThatThrownBy(() ->
                signatureService.sign(
                        document.getId(), docVersion.getId(), stepInstance.getId(),
                        PLAIN_PASSWORD, "REVIEWED",
                        null,   // signingUserId 누락
                        auth, session, "127.0.0.1"))
                .isInstanceOf(UnprocessableEntityException.class)
                .satisfies(e -> assertThat(((UnprocessableEntityException) e).getCode())
                        .isEqualTo("SIGNATURE_002"));
    }

    // ===== OQ-SIG-010b: signingUserId 불일치 → 403 ForbiddenException =====
    @Test
    void sessionFirst_userIdMismatch_returns403() {
        MockHttpSession session = new MockHttpSession();
        Authentication auth = authOf(testUser.getUserId());

        assertThatThrownBy(() ->
                signatureService.sign(
                        document.getId(), docVersion.getId(), stepInstance.getId(),
                        PLAIN_PASSWORD, "REVIEWED",
                        "other_user",   // 인증 사용자와 불일치
                        auth, session, "127.0.0.1"))
                .isInstanceOf(ForbiddenException.class);
    }

    // ===== FLAG_IDEMPOTENCY: PW 실패 → 플래그 소비 안 됨 → 재시도 성공 =====
    @Test
    void flagIdempotency_pwFailDoesNotConsumeSessionFirst() {
        MockHttpSession session = new MockHttpSession();
        Authentication auth = authOf(testUser.getUserId());

        // 1차: 잘못된 PW → UnauthorizedException (플래그는 소비되지 않아야 함)
        assertThatThrownBy(() ->
                signatureService.sign(
                        document.getId(), docVersion.getId(), stepInstance.getId(),
                        "WRONG_PASS", "REVIEWED",
                        testUser.getUserId(), auth, session, "127.0.0.1"))
                .isInstanceOf(UnauthorizedException.class);

        // failed_attempts 초기화 (잠금 방지)
        jdbc.update("UPDATE users SET failed_attempts = 0 WHERE user_id = ?",
                testUser.getUserId());

        // 2차: 올바른 PW + signingUserId → 성공, sessionFirst=true 유지
        SignatureManifest manifest = signatureService.sign(
                document.getId(), docVersion.getId(), stepInstance.getId(),
                PLAIN_PASSWORD, "REVIEWED",
                testUser.getUserId(), auth, session, "127.0.0.1");

        assertThat(manifest).isNotNull();
        assertThat(manifest.isSessionFirst()).isTrue();
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
        doc.setDocNumber("SOP-QC-FIRST-" + System.nanoTime());
        doc.setCategoryId(sopCategoryId);
        doc.setDepartment("QC");
        doc.setTitle("first-sign 테스트 문서");
        doc.setOwnerId(testUser != null ? testUser.getId() : 1L);
        doc.setCreatedBy(testUser != null ? testUser.getId() : 1L);
        doc.setConfidential(false);
        return docRepo.save(doc);
    }

    private DocumentVersion createDocVersion(Long docId, Long createdBy) {
        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(docId);
        v.setState("UNDER_REVIEW");
        v.setTitle("first-sign 테스트 버전");
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
