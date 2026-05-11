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
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * SignatureChainIntegrityIT
 *
 * v_signature_chain_integrity 뷰 검증:
 * 동일 version_id 에 3명이 순차 서명 후 broken_links == 0 임을 확인한다.
 *
 * min_signers=3, parallel=true 단일 step 에 user1→user2→user3 순서로 서명.
 * 각 manifest 의 prev_hash 체인이 올바르게 이어지는지도 직접 검증한다.
 *
 * @Transactional 없이 실행 — 실제 DB 커밋이 필요하다. @AfterEach 에서 수동 정리한다.
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
class SignatureChainIntegrityIT {

    private static final String TEST_SHA256 =
            "cafebabe1234567890abcdef1234567890abcdef1234567890abcdef12345678";
    private static final String PLAIN_PASSWORD = "Test@1234";

    // SHA-256("GENESIS") — 체인 첫 번째 row 의 prev_hash 기댓값
    private static final String GENESIS_HASH;
    static {
        try {
            java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest("GENESIS".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            GENESIS_HASH = sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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

    private User user1;
    private User user2;
    private User user3;
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
        jdbc.execute("DELETE FROM user_roles");
        jdbc.execute("DELETE FROM users WHERE user_id LIKE 'chain_integrity_test_%'");
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

            user1 = createUser("chain_integrity_test_01", PLAIN_PASSWORD);
            user2 = createUser("chain_integrity_test_02", PLAIN_PASSWORD);
            user3 = createUser("chain_integrity_test_03", PLAIN_PASSWORD);

            Role reviewerRole = roleRepo.findByRoleCode("REVIEWER").orElseThrow();
            assignRole(user1, reviewerRole);
            assignRole(user2, reviewerRole);
            assignRole(user3, reviewerRole);
            em.flush();
            em.clear();

            grantPermission(reviewerRole.getId(), sopCategoryId, "QC", true, false);
            em.flush();
            em.clear();

            document = createDocument();
            docVersion = createDocVersion(document.getId(), user1.getId());
            insertOriginalFile(docVersion.getId(), user1.getId(), TEST_SHA256);
            em.flush();
            em.clear();

            wfInstance = new WorkflowInstance();
            wfInstance.setVersionId(docVersion.getId());
            wfInstance.setTemplateId(1L);
            wfInstance.setState("IN_PROGRESS");
            wfInstance.setCurrentStep(1);
            wfInstance.setStartedBy(user1.getUserId());
            wfInstanceRepo.save(wfInstance);

            // min_signers=3, parallel=true — 3명 모두 서명해야 COMPLETED
            stepInstance = createStepInstance(wfInstance.getId(), 1,
                    List.of(user1, user2, user3), 3, true, "REVIEW");
            stepInstance.setState("IN_PROGRESS");
            wfStepRepo.save(stepInstance);
            em.flush();
            em.clear();

            return null;
        });
    }

    /**
     * 3회 순차 서명 후 v_signature_chain_integrity 뷰의 broken_links == 0
     *
     * 추가 검증:
     *   - total == 3
     *   - 첫 번째 manifest.prev_hash == GENESIS_HASH
     *   - 각 후속 manifest.prev_hash == 직전 manifest.this_hash
     */
    @Test
    void threeSequentialSignatures_chainIntegrityViewShowsZeroBrokenLinks() {
        long versionId = docVersion.getId();

        // ── 1차 서명 (user1) ──
        MockHttpSession session1 = new MockHttpSession();
        signatureService.sign(
                document.getId(), versionId, stepInstance.getId(),
                PLAIN_PASSWORD, "REVIEWED", authOf(user1.getUserId()), session1, "127.0.0.1");

        // ── 2차 서명 (user2) ──
        MockHttpSession session2 = new MockHttpSession();
        signatureService.sign(
                document.getId(), versionId, stepInstance.getId(),
                PLAIN_PASSWORD, "REVIEWED", authOf(user2.getUserId()), session2, "127.0.0.2");

        // ── 3차 서명 (user3) ──
        MockHttpSession session3 = new MockHttpSession();
        signatureService.sign(
                document.getId(), versionId, stepInstance.getId(),
                PLAIN_PASSWORD, "REVIEWED", authOf(user3.getUserId()), session3, "127.0.0.3");

        // ── v_signature_chain_integrity 뷰 검증 ──
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT total, broken_links FROM v_signature_chain_integrity WHERE version_id = ?",
                versionId);

        assertThat(((Number) row.get("total")).intValue())
                .as("v2 알고리즘 서명 총 건수")
                .isEqualTo(3);
        assertThat(((Number) row.get("broken_links")).intValue())
                .as("체인 단절 건수 — 0이어야 GxP §11.10(e) 충족")
                .isEqualTo(0);

        // ── 해시 체인 직접 검증 ──
        List<Map<String, Object>> manifests = jdbc.queryForList(
                "SELECT id, prev_hash, this_hash FROM signature_manifests " +
                "WHERE version_id = ? AND algorithm_version = 'v2' ORDER BY id",
                versionId);

        assertThat(manifests).hasSize(3);

        // 첫 번째 row: prev_hash == GENESIS_HASH
        String firstPrevHash = (String) manifests.get(0).get("prev_hash");
        assertThat(firstPrevHash)
                .as("첫 번째 manifest의 prev_hash는 GENESIS_HASH여야 한다")
                .isEqualTo(GENESIS_HASH);

        // 각 후속 row: prev_hash == 직전 row의 this_hash
        for (int i = 1; i < manifests.size(); i++) {
            String expectedPrev = (String) manifests.get(i - 1).get("this_hash");
            String actualPrev   = (String) manifests.get(i).get("prev_hash");
            assertThat(actualPrev)
                    .as("manifest[%d].prev_hash는 manifest[%d].this_hash와 일치해야 한다", i, i - 1)
                    .isEqualTo(expectedPrev);
        }
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
        doc.setDocNumber("SOP-QC-CHAIN-" + System.nanoTime());
        doc.setCategoryId(sopCategoryId);
        doc.setDepartment("QC");
        doc.setTitle("체인 무결성 테스트 문서");
        doc.setOwnerId(user1.getId());
        doc.setCreatedBy(user1.getId());
        doc.setConfidential(false);
        return docRepo.save(doc);
    }

    private DocumentVersion createDocVersion(Long docId, Long createdBy) {
        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(docId);
        v.setState("UNDER_REVIEW");
        v.setTitle("체인 무결성 테스트 버전");
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
