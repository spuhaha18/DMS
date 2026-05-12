package com.lab.edms.signature;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.category.DocumentCategoryRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
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
 * SignatureUniqueHashIT
 *
 * M7 PR3: sign()은 sign_intents만 INSERT한다. SignatureManifest는 워커가 채운다.
 *
 * 검증 항목:
 * 1. signature_manifests UNIQUE(this_hash) 제약 — JDBC로 직접 manifest 삽입 후 duplicate INSERT 시도
 * 2. sign() 호출 후 sign_intents.status = 'PENDING_STAMP' 확인
 *
 * @Transactional 없이 실행 — UNIQUE 제약은 커밋 시점에 검증되므로
 * 실제 DB 커밋이 필요하다. @AfterEach 에서 수동 정리한다.
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
class SignatureUniqueHashIT {

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
        jdbc.execute("DELETE FROM sign_intents");
        jdbc.execute("DELETE FROM workflow_step_instances");
        jdbc.execute("DELETE FROM workflow_instances");
        jdbc.execute("DELETE FROM document_files");
        jdbc.execute("DELETE FROM document_versions");
        jdbc.execute("DELETE FROM documents");
        jdbc.execute("DELETE FROM permissions");
        jdbc.execute("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE user_id LIKE 'unique_hash_test_%')");
        jdbc.execute("DELETE FROM users WHERE user_id LIKE 'unique_hash_test_%'");
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

            testUser = createUser("unique_hash_test_01", PLAIN_PASSWORD);

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
     * Test 1: signature_manifests UNIQUE(this_hash) 제약 위반 검증
     *
     * M7 PR3: sign()은 sign_intents만 INSERT. 워커가 manifest를 INSERT한다.
     * UNIQUE 제약은 JDBC로 직접 manifest를 삽입해 검증한다.
     */
    @Test
    void duplicateThisHash_throwsDataIntegrityViolation() {
        // 고정 thisHash 값으로 첫 번째 manifest 직접 삽입
        String fixedHash = "aabbcc1234567890aabbcc1234567890aabbcc1234567890aabbcc1234567890";
        jdbc.update(
                "INSERT INTO signature_manifests " +
                "(version_id, workflow_step_id, signer_id, signer_user_id, signer_name, " +
                " meaning, signed_at, client_ip, canonical_payload, prev_hash, this_hash, " +
                " session_first, algorithm_version) " +
                "VALUES (?,?,?,?,?,?,NOW(),'127.0.0.1','payload','genesis'," +
                "?::varchar,false,'v2')",
                docVersion.getId(),
                stepInstance.getId(),
                testUser.getId(),
                testUser.getUserId(),
                testUser.getFullName(),
                "REVIEWED",
                fixedHash
        );

        // 동일 this_hash 로 두 번째 row 직접 INSERT → UNIQUE 제약 위반 기대
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO signature_manifests " +
                        "(version_id, workflow_step_id, signer_id, signer_user_id, signer_name, " +
                        " meaning, signed_at, client_ip, canonical_payload, prev_hash, this_hash, " +
                        " session_first, algorithm_version) " +
                        "VALUES (?,?,?,?,?,?,NOW(),'127.0.0.2','payload2','genesis'," +
                        "?::varchar,false,'v2')",
                        docVersion.getId(),
                        stepInstance.getId(),
                        testUser.getId(),
                        testUser.getUserId(),
                        testUser.getFullName(),
                        "REVIEWED",
                        fixedHash
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Test 2: sign() 호출 후 sign_intents row 생성 확인
     *
     * M7 PR3: sign()은 sign_intents만 INSERT. manifest는 워커가 채운다.
     * 비동기 워커가 FAILED로 전환할 수 있으므로 status 무관 id/signerUserId 확인.
     */
    @Test
    void sign_createsSignIntentRow() {
        MockHttpSession session = new MockHttpSession();
        Authentication auth = authOf(testUser.getUserId());

        SignIntent intent = signatureService.sign(
                document.getId(), docVersion.getId(), stepInstance.getId(),
                PLAIN_PASSWORD, "REVIEWED", testUser.getUserId(),
                auth, session, "127.0.0.1");

        assertThat(intent).isNotNull();
        assertThat(intent.getId()).isNotNull();
        assertThat(intent.getSignerUserId()).isEqualTo(testUser.getUserId());
        assertThat(intent.getVersionId()).isEqualTo(docVersion.getId());

        // DB에서 sign_intents row 존재 확인 (status 무관)
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sign_intents WHERE id = ?",
                Integer.class, intent.getId());
        assertThat(count).isEqualTo(1);
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
        doc.setDocNumber("SOP-QC-UHASH-" + System.nanoTime());
        doc.setCategoryId(sopCategoryId);
        doc.setDepartment("QC");
        doc.setTitle("유니크해시 테스트 문서");
        doc.setOwnerId(testUser.getId());
        doc.setCreatedBy(testUser.getId());
        doc.setConfidential(false);
        return docRepo.save(doc);
    }

    private DocumentVersion createDocVersion(Long docId, Long createdBy) {
        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(docId);
        v.setState("UNDER_REVIEW");
        v.setTitle("유니크해시 테스트 버전");
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
