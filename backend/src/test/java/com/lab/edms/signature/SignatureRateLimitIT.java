package com.lab.edms.signature;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.common.TooManyRequestsException;
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
 * SignatureRateLimitIT — OQ-SIG-RATE-001, OQ-SIG-RATE-002
 *
 * Rate Limiter 엔드-투-엔드 검증:
 * 1. 동일 userId+IP 5회 초과 → TooManyRequestsException (429)
 * 2. 동일 userId, 다른 IP → 별도 버킷 → 6번째도 UnauthorizedException (rate limit 아님)
 *
 * 주의:
 * - @DirtiesContext: 테스트 간 in-memory 버킷 격리
 * - 각 테스트마다 고유 userId prefix 사용 (sig_rate_001, sig_rate_002)
 * - 5회 PW 실패로 인한 계정 LOCKED 방지: 각 시도 후 failed_attempts 리셋
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
class SignatureRateLimitIT {

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

    // Test 1: userId1 + IP "127.0.0.1"
    private User user1;
    private Document doc1;
    private DocumentVersion ver1;
    private WorkflowInstance wf1;
    private WorkflowStepInstance step1;

    // Test 2: userId2 + IP "1.2.3.4" / "5.6.7.8"
    private User user2;
    private Document doc2;
    private DocumentVersion ver2;
    private WorkflowInstance wf2;
    private WorkflowStepInstance step2;

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
        jdbc.execute("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE user_id LIKE 'sig_rate_%')");
        jdbc.execute("DELETE FROM users WHERE user_id LIKE 'sig_rate_%'");
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

            Role reviewerRole = roleRepo.findByRoleCode("REVIEWER").orElseThrow();

            // user1: rate limit 테스트용
            user1 = createUser("sig_rate_001", PLAIN_PASSWORD);
            assignRole(user1, reviewerRole);
            em.flush();
            em.clear();
            grantPermission(reviewerRole.getId(), sopCategoryId, "QC", true, false);
            em.flush();
            em.clear();

            doc1 = createDocument("sig_rate_001");
            ver1 = createDocVersion(doc1.getId(), user1.getId());
            insertOriginalFile(ver1.getId(), user1.getId(), TEST_SHA256);
            em.flush();
            em.clear();

            wf1 = new WorkflowInstance();
            wf1.setVersionId(ver1.getId());
            wf1.setTemplateId(1L);
            wf1.setState("IN_PROGRESS");
            wf1.setCurrentStep(1);
            wf1.setStartedBy(user1.getUserId());
            wfInstanceRepo.save(wf1);

            step1 = createStepInstance(wf1.getId(), 1, List.of(user1), 1, false, "REVIEW");
            step1.setState("IN_PROGRESS");
            wfStepRepo.save(step1);
            em.flush();
            em.clear();

            // user2: 다른 IP 버킷 테스트용
            user2 = createUser("sig_rate_002", PLAIN_PASSWORD);
            assignRole(user2, reviewerRole);
            em.flush();
            em.clear();

            doc2 = createDocument("sig_rate_002");
            ver2 = createDocVersion(doc2.getId(), user2.getId());
            insertOriginalFile(ver2.getId(), user2.getId(), TEST_SHA256);
            em.flush();
            em.clear();

            wf2 = new WorkflowInstance();
            wf2.setVersionId(ver2.getId());
            wf2.setTemplateId(1L);
            wf2.setState("IN_PROGRESS");
            wf2.setCurrentStep(1);
            wf2.setStartedBy(user2.getUserId());
            wfInstanceRepo.save(wf2);

            step2 = createStepInstance(wf2.getId(), 1, List.of(user2), 1, false, "REVIEW");
            step2.setState("IN_PROGRESS");
            wfStepRepo.save(step2);
            em.flush();
            em.clear();

            return null;
        });
    }

    /**
     * OQ-SIG-RATE-001: 동일 userId+IP 조합으로 5회 요청 후 6번째 → 429 TooManyRequestsException
     *
     * 5회는 모두 잘못된 PW → UnauthorizedException (rate limit 아님)
     * 6번째: rate limiter가 먼저 동작 → TooManyRequestsException
     *
     * 계정 LOCKED 방지: 각 시도 후 failed_attempts 리셋
     */
    @Test
    void sixRequestsSameUserIp_sixthReturns429() {
        Authentication auth = authOf(user1.getUserId());

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() ->
                    signatureService.sign(
                            doc1.getId(), ver1.getId(), step1.getId(),
                            "WRONG_PASSWORD", "REVIEWED", user1.getUserId(),
                            auth, new MockHttpSession(), "127.0.0.1"))
                    .isInstanceOf(UnauthorizedException.class);

            // 계정 잠금(LOCKED) 방지: failed_attempts 리셋
            jdbc.update("UPDATE users SET failed_attempts = 0 WHERE user_id = ?",
                    user1.getUserId());
        }

        // 6번째: rate limit 초과 → TooManyRequestsException
        assertThatThrownBy(() ->
                signatureService.sign(
                        doc1.getId(), ver1.getId(), step1.getId(),
                        "WRONG_PASSWORD", "REVIEWED", user1.getUserId(),
                        auth, new MockHttpSession(), "127.0.0.1"))
                .as("6번째 요청은 TooManyRequestsException이어야 한다")
                .isInstanceOf(TooManyRequestsException.class);
    }

    /**
     * OQ-SIG-RATE-002: 동일 userId, 다른 IP → 별도 버킷 → rate limit 미적용
     *
     * userId2 + IP "1.2.3.4"로 5회 요청 후
     * userId2 + IP "5.6.7.8"(다른 버킷)로 6번째 요청 → rate limit 아님 → UnauthorizedException
     */
    @Test
    void differentIp_hasOwnBucket() {
        Authentication auth = authOf(user2.getUserId());

        // IP "1.2.3.4"로 5회 요청 → 버킷 소진
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() ->
                    signatureService.sign(
                            doc2.getId(), ver2.getId(), step2.getId(),
                            "WRONG_PASSWORD", "REVIEWED", user2.getUserId(),
                            auth, new MockHttpSession(), "1.2.3.4"))
                    .isInstanceOf(UnauthorizedException.class);

            // 계정 잠금(LOCKED) 방지: failed_attempts 리셋
            jdbc.update("UPDATE users SET failed_attempts = 0 WHERE user_id = ?",
                    user2.getUserId());
        }

        // 다른 IP "5.6.7.8"로 요청 → 별도 버킷(5회 여유) → rate limit 아님
        assertThatThrownBy(() ->
                signatureService.sign(
                        doc2.getId(), ver2.getId(), step2.getId(),
                        "WRONG_PASSWORD", "REVIEWED", user2.getUserId(),
                        auth, new MockHttpSession(), "5.6.7.8"))
                .as("다른 IP는 별도 버킷이므로 TooManyRequestsException이 아니어야 한다")
                .isInstanceOf(UnauthorizedException.class);
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

    private Document createDocument(String suffix) {
        Document doc = new Document();
        doc.setDocNumber("SOP-QC-RATE-" + suffix + "-" + System.nanoTime());
        doc.setCategoryId(sopCategoryId);
        doc.setDepartment("QC");
        doc.setTitle("Rate Limit 테스트 문서 " + suffix);
        doc.setOwnerId(user1 != null ? user1.getId() : 1L);
        doc.setCreatedBy(user1 != null ? user1.getId() : 1L);
        doc.setConfidential(false);
        return docRepo.save(doc);
    }

    private DocumentVersion createDocVersion(Long docId, Long createdBy) {
        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(docId);
        v.setState("UNDER_REVIEW");
        v.setTitle("Rate Limit 테스트 버전");
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
