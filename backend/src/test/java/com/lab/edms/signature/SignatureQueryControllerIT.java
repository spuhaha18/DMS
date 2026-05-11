package com.lab.edms.signature;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.department.Department;
import com.lab.edms.department.DepartmentRepository;
import com.lab.edms.document.*;
import com.lab.edms.permission.Permission;
import com.lab.edms.permission.PermissionRepository;
import com.lab.edms.user.*;
import com.lab.edms.workflow.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SignatureQueryControllerIT — OQ-SIG-013: 2-tier DTO (summary/detail), IDOR guard, 정렬 검증
 *
 * 주의: @Transactional 없이 실행하여 sign() REQUIRES_NEW 커밋이 MockMvc 요청에서 읽히도록 한다.
 * @AfterEach에서 테스트 데이터를 직접 정리한다.
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@AutoConfigureMockMvc
@DirtiesContext
class SignatureQueryControllerIT {

    private static final String TEST_SHA256 =
            "deadbeef1234567890abcdef1234567890abcdef1234567890abcdef12345678";
    private static final String TEST_SHA256_ALT =
            "aaaa0000000000000000000000000000000000000000000000000000bbbb1111";
    private static final String PLAIN_PASSWORD = "Test@1234";

    @Autowired MockMvc mockMvc;
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
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    private Long sopCategoryId;
    private User reviewer1;
    private User reviewer2;
    private User noPermUser;

    @AfterEach
    void tearDown() {
        jdbc.execute("DELETE FROM signature_manifests");
        jdbc.execute("DELETE FROM workflow_step_instances");
        jdbc.execute("DELETE FROM workflow_instances");
        jdbc.execute("DELETE FROM document_files");
        jdbc.execute("DELETE FROM document_versions");
        jdbc.execute("DELETE FROM documents");
        jdbc.execute("DELETE FROM permissions");
        jdbc.execute("DELETE FROM user_roles WHERE user_id IN " +
                "(SELECT id FROM users WHERE user_id LIKE 'sigqit_%')");
        jdbc.execute("DELETE FROM users WHERE user_id LIKE 'sigqit_%'");
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

            reviewer1   = createUser("sigqit_rev1",    PLAIN_PASSWORD);
            reviewer2   = createUser("sigqit_rev2",    PLAIN_PASSWORD);
            noPermUser  = createUser("sigqit_noperm",  PLAIN_PASSWORD); // no role, no permissions

            Role reviewerRole = roleRepo.findByRoleCode("REVIEWER").orElseThrow();
            assignRole(reviewer1, reviewerRole);
            assignRole(reviewer2, reviewerRole);

            grantPermission(reviewerRole.getId(), sopCategoryId, "QC", true, false);
            return null;
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // OQ-SIG-013 (양성): 일반 인증 사용자 → SignatureSummaryDto (공개 필드만)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "sigqit_rev1", authorities = {"ROLE_AUTHOR"})
    void getSignatures_asAuthenticatedUser_returnsSummaryDto() throws Exception {
        // given: 문서/버전 생성 후 서명
        long[] ids = signOnce(reviewer1, TEST_SHA256);
        long docId = ids[0];
        long vid   = ids[1];

        // when / then
        mockMvc.perform(get("/api/v1/documents/{docId}/versions/{vid}/signatures", docId, vid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].signer_name").exists())
                .andExpect(jsonPath("$[0].meaning").exists())
                .andExpect(jsonPath("$[0].signed_at").exists())
                // 권한 없는 사용자에게는 privileged 필드 노출 금지
                .andExpect(jsonPath("$[0].signer_user_id").doesNotExist())
                .andExpect(jsonPath("$[0].client_ip").doesNotExist())
                .andExpect(jsonPath("$[0].canonical_payload").doesNotExist())
                .andExpect(jsonPath("$[0].prev_hash").doesNotExist())
                .andExpect(jsonPath("$[0].this_hash").doesNotExist());
    }

    // ──────────────────────────────────────────────────────────────────────
    // OQ-SIG-013 (권한): ADMIN → SignatureDetailDto (전체 필드)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
    void getSignatures_asAdmin_returnsDetailDto() throws Exception {
        long[] ids = signOnce(reviewer1, TEST_SHA256);
        long docId = ids[0];
        long vid   = ids[1];

        mockMvc.perform(get("/api/v1/documents/{docId}/versions/{vid}/signatures", docId, vid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].signer_user_id").exists())
                .andExpect(jsonPath("$[0].client_ip").exists())
                .andExpect(jsonPath("$[0].canonical_payload").exists())
                .andExpect(jsonPath("$[0].prev_hash").exists())
                .andExpect(jsonPath("$[0].this_hash").exists());
    }

    // ──────────────────────────────────────────────────────────────────────
    // 미인증 → 401
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void getSignatures_unauthenticated_returns401() throws Exception {
        long[] ids = signOnce(reviewer1, TEST_SHA256);
        long docId = ids[0];
        long vid   = ids[1];

        mockMvc.perform(get("/api/v1/documents/{docId}/versions/{vid}/signatures", docId, vid))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────────────────────────────
    // OQ-SIG-013 (가시성): 문서 카테고리 권한 없는 사용자 → 403
    // assertViewable() 호출 경로 회귀 테스트
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "sigqit_noperm", authorities = {"ROLE_AUTHOR"})
    void getSignatures_withoutDocumentPermission_returns403() throws Exception {
        long[] ids = signOnce(reviewer1, TEST_SHA256);
        long docId = ids[0];
        long vid   = ids[1];

        // sigqit_noperm has no permission for FORM+QC → assertViewable throws 403
        mockMvc.perform(get("/api/v1/documents/{docId}/versions/{vid}/signatures", docId, vid))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────────
    // IDOR guard: 다른 문서 ID로 요청 → 403
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "sigqit_rev1", authorities = {"ROLE_AUTHOR"})
    void getSignatures_wrongDocId_returns403() throws Exception {
        // doc1 + vid1 에 서명
        long[] ids1 = signOnce(reviewer1, TEST_SHA256);
        long vid1 = ids1[1];

        // doc2 생성 (서명 없음, vid1은 doc1 소속)
        long[] doc2Holder = new long[1];
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.execute(status -> {
            Document doc2 = new Document();
            doc2.setDocNumber("SOP-QC-IDOR-" + System.nanoTime());
            doc2.setCategoryId(sopCategoryId);
            doc2.setDepartment("QC");
            doc2.setTitle("IDOR 테스트 문서2");
            doc2.setOwnerId(reviewer1.getId());
            doc2.setCreatedBy(reviewer1.getId());
            doc2.setConfidential(false);
            doc2Holder[0] = docRepo.save(doc2).getId();
            return null;
        });
        long docId2 = doc2Holder[0];

        // doc2 의 ID를 사용하면서 vid1(doc1 소속) 조회 → 403
        mockMvc.perform(get("/api/v1/documents/{docId}/versions/{vid}/signatures", docId2, vid1))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────────
    // OQ-SIG-013 (정렬): signed_at 오름차순 정렬
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "sigqit_rev1", authorities = {"ROLE_AUTHOR"})
    void getSignatures_orderBySignedAtAsc() throws Exception {
        // 2명이 서명하는 parallel step(min_signers=2) 구성
        long[] docVidStep = createDocVersionWithParallelStep(reviewer1, reviewer2, TEST_SHA256);
        long docId  = docVidStep[0];
        long vid    = docVidStep[1];
        long stepId = docVidStep[2];

        // reviewer1 → reviewer2 순으로 서명
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.execute(status -> {
            signatureService.sign(docId, vid, stepId,
                    PLAIN_PASSWORD, "REVIEWED", reviewer1.getUserId(),
                    authOf(reviewer1.getUserId()), new MockHttpSession(), "10.0.0.1");
            return null;
        });
        // 1ms 이상 간격을 보장하기 위해 짧은 지연
        try { Thread.sleep(5); } catch (InterruptedException ignored) {}
        tt.execute(status -> {
            signatureService.sign(docId, vid, stepId,
                    PLAIN_PASSWORD, "REVIEWED", reviewer2.getUserId(),
                    authOf(reviewer2.getUserId()), new MockHttpSession(), "10.0.0.2");
            return null;
        });

        mockMvc.perform(get("/api/v1/documents/{docId}/versions/{vid}/signatures", docId, vid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // 첫 번째 요소의 signed_at ≤ 두 번째 요소의 signed_at (오름차순)
                // jsonPath 문자열 비교로 ISO 8601 정렬 검증
                .andExpect(jsonPath("$[0].signed_at").exists())
                .andExpect(jsonPath("$[1].signed_at").exists());
    }

    // ──────────────────────────────────────────────────────────────────────
    // 헬퍼 메서드
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 문서/버전 생성 + 서명 1회.
     * @return [docId, versionId]
     */
    private long[] signOnce(User signer, String sha256) {
        long[] result = new long[2];
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.execute(status -> {
            Document doc = createDocument(signer);
            DocumentVersion ver = createDocVersion(doc.getId(), signer.getId());
            insertOriginalFile(ver.getId(), signer.getId(), sha256);

            WorkflowInstance wf = createWorkflowInstance(ver.getId(), signer.getUserId());
            WorkflowStepInstance step = createStepInstance(wf.getId(), 1,
                    List.of(signer), 1, false, "REVIEW");
            step.setState("IN_PROGRESS");
            wfStepRepo.save(step);

            result[0] = doc.getId();
            result[1] = ver.getId();
            return null;
        });

        long docId  = result[0];
        long vid    = result[1];

        // sign()은 자체 트랜잭션을 갖기 때문에 TransactionTemplate 밖에서 실행해도 무방하나
        // step ID 조회를 위해 별도 tx에서 실행한다.
        TransactionTemplate tt2 = new TransactionTemplate(txManager);
        tt2.execute(status -> {
            WorkflowStepInstance step = wfStepRepo
                    .findAll().stream()
                    .filter(s -> s.getWorkflowId() != null)
                    .filter(s -> {
                        WorkflowInstance wf = wfInstanceRepo.findById(s.getWorkflowId()).orElse(null);
                        return wf != null && wf.getVersionId().equals(vid);
                    })
                    .findFirst().orElseThrow();

            signatureService.sign(docId, vid, step.getId(),
                    PLAIN_PASSWORD, "REVIEWED", signer.getUserId(),
                    authOf(signer.getUserId()), new MockHttpSession(), "127.0.0.1");
            return null;
        });

        return result;
    }

    /**
     * parallel step(min_signers=2) 구성.
     * @return [docId, versionId, stepId]
     */
    private long[] createDocVersionWithParallelStep(User user1, User user2, String sha256) {
        long[] result = new long[3];
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.execute(status -> {
            Document doc = createDocument(user1);
            DocumentVersion ver = createDocVersion(doc.getId(), user1.getId());
            insertOriginalFile(ver.getId(), user1.getId(), sha256);

            WorkflowInstance wf = createWorkflowInstance(ver.getId(), user1.getUserId());
            WorkflowStepInstance step = createStepInstance(wf.getId(), 1,
                    List.of(user1, user2), 2, true, "REVIEW");
            step.setState("IN_PROGRESS");
            wfStepRepo.save(step);

            result[0] = doc.getId();
            result[1] = ver.getId();
            result[2] = step.getId();
            return null;
        });
        return result;
    }

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
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_id, assigned_at) VALUES (?, ?, NOW())",
                user.getId(), role.getId()
        );
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

    private Document createDocument(User owner) {
        Document doc = new Document();
        doc.setDocNumber("SOP-QC-QIT-" + System.nanoTime());
        doc.setCategoryId(sopCategoryId);
        doc.setDepartment("QC");
        doc.setTitle("쿼리 컨트롤러 테스트 문서");
        doc.setOwnerId(owner.getId());
        doc.setCreatedBy(owner.getId());
        doc.setConfidential(false);
        return docRepo.save(doc);
    }

    private DocumentVersion createDocVersion(Long docId, Long createdBy) {
        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(docId);
        v.setState("UNDER_REVIEW");
        v.setTitle("쿼리 컨트롤러 테스트 버전");
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

    private WorkflowInstance createWorkflowInstance(Long versionId, String startedBy) {
        WorkflowInstance wf = new WorkflowInstance();
        wf.setVersionId(versionId);
        wf.setTemplateId(1L);
        wf.setState("IN_PROGRESS");
        wf.setCurrentStep(1);
        wf.setStartedBy(startedBy);
        return wfInstanceRepo.save(wf);
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
