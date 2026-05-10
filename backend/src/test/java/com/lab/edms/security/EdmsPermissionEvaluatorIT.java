package com.lab.edms.security;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.category.DocumentCategory;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.department.Department;
import com.lab.edms.department.DepartmentRepository;
import com.lab.edms.document.Document;
import com.lab.edms.document.DocumentRepository;
import com.lab.edms.permission.Permission;
import com.lab.edms.permission.PermissionRepository;
import com.lab.edms.user.Role;
import com.lab.edms.user.RoleRepository;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EdmsPermissionEvaluator 통합 테스트 — 5개 시나리오:
 * 1. Author/SOP/QC can_create=true → CREATE = true
 * 2. 동일 사용자, SOP/QA 부서 → CREATE = false (부서 다름)
 * 3. Reviewer, department=null(전체부서) → SOP/QC와 SOP/QA 모두 REVIEW = true
 * 4. QA role, can_approve=true → APPROVE = true
 * 5. 권한 없는 사용자 → 모든 액션 false
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class EdmsPermissionEvaluatorIT {

    @Autowired
    private EdmsPermissionEvaluator evaluator;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private RoleRepository roleRepo;

    @Autowired
    private PermissionRepository permissionRepo;

    @Autowired
    private DocumentCategoryRepository categoryRepo;

    @Autowired
    private DocumentRepository documentRepo;

    @Autowired
    private DepartmentRepository departmentRepo;

    @Autowired
    private EntityManager em;

    // 테스트 데이터
    private Long sopCategoryId;
    private Document qcDoc;   // SOP / QC 부서 문서
    private Document qaDoc;   // SOP / QA 부서 문서

    @BeforeEach
    void setUp() {
        // 0. departments 테이블에 QC, QA 추가 (fk_documents_dept 제약 때문에 필요)
        createDepartmentIfAbsent("QC", "Quality Control");
        createDepartmentIfAbsent("QA", "Quality Assurance");
        em.flush();

        // 1. SOP 카테고리 조회 (V12 seed로 이미 존재)
        DocumentCategory sop = categoryRepo.findByCategoryCode("SOP")
                .orElseGet(() -> {
                    DocumentCategory c = new DocumentCategory();
                    c.setCategoryCode("SOP_IT_TEST");
                    c.setCategoryName("SOP IT Test");
                    c.setReviewPeriodMonths(24);
                    c.setQaMandatory(false);
                    c.setActive(true);
                    return categoryRepo.save(c);
                });
        sopCategoryId = sop.getId();

        // 2. 역할 생성 (시스템 역할이 이미 있으므로 새로 생성)
        Role authorRole = createRole("AUTHOR_IT", "작성자_IT");
        Role reviewerRole = createRole("REVIEWER_IT", "검토자_IT");
        Role qaRole = createRole("QA_IT", "QA_IT");
        Role noPermRole = createRole("NOPERM_IT", "권한없음_IT");

        // 3. 사용자 생성
        User authorUser = createUser("author_it", "Author IT", "QC");
        User reviewerUser = createUser("reviewer_it", "Reviewer IT", "QC");
        User qaUser = createUser("qa_it", "QA IT", "QA");
        User noPermUser = createUser("noperm_it", "NoPerm IT", "QC");

        // 4. 사용자-역할 연결
        assignRole(authorUser, authorRole);
        assignRole(reviewerUser, reviewerRole);
        assignRole(qaUser, qaRole);
        // noPermUser는 역할 없음

        em.flush();

        // 5. 권한 설정
        // Author: SOP/QC → can_create=true, can_edit_draft=true, can_view=true
        Permission authorPerm = new Permission();
        authorPerm.setRoleId(authorRole.getId());
        authorPerm.setCategoryId(sopCategoryId);
        authorPerm.setDepartment("QC");
        authorPerm.setCanView(true);
        authorPerm.setCanDownload(true);
        authorPerm.setCanCreate(true);
        authorPerm.setCanEditDraft(true);
        authorPerm.setCanReview(false);
        authorPerm.setCanApprove(false);
        authorPerm.setCanRetire(false);
        permissionRepo.save(authorPerm);

        // Reviewer: SOP / department=null (전체 부서) → can_review=true
        Permission reviewerPerm = new Permission();
        reviewerPerm.setRoleId(reviewerRole.getId());
        reviewerPerm.setCategoryId(sopCategoryId);
        reviewerPerm.setDepartment(null);
        reviewerPerm.setCanView(true);
        reviewerPerm.setCanDownload(true);
        reviewerPerm.setCanCreate(false);
        reviewerPerm.setCanEditDraft(false);
        reviewerPerm.setCanReview(true);
        reviewerPerm.setCanApprove(false);
        reviewerPerm.setCanRetire(false);
        permissionRepo.save(reviewerPerm);

        // QA: SOP / QA 부서 → can_approve=true
        Permission qaPerm = new Permission();
        qaPerm.setRoleId(qaRole.getId());
        qaPerm.setCategoryId(sopCategoryId);
        qaPerm.setDepartment("QA");
        qaPerm.setCanView(true);
        qaPerm.setCanDownload(true);
        qaPerm.setCanCreate(false);
        qaPerm.setCanEditDraft(false);
        qaPerm.setCanReview(false);
        qaPerm.setCanApprove(true);
        qaPerm.setCanRetire(false);
        permissionRepo.save(qaPerm);

        em.flush();

        // 6. 테스트용 Document 생성
        qcDoc = createDocument("SOP-QC-IT-001", sopCategoryId, "QC", authorUser.getId());
        qaDoc = createDocument("SOP-QA-IT-001", sopCategoryId, "QA", authorUser.getId());

        em.flush();
        em.clear();
    }

    // 인증된 Authentication 생성 헬퍼 (UsernamePasswordAuthenticationToken 2-arg는 authenticated=false)
    private static Authentication authenticated(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of());
    }

    // 시나리오 1: Author/SOP/QC → CREATE = true
    @Test
    void scenario1_author_sopQc_create_returnsTrue() {
        boolean result = evaluator.hasPermission(authenticated("author_it"), qcDoc, "CREATE");
        assertThat(result).isTrue();
    }

    // 시나리오 2: Author/SOP/QA 부서 → CREATE = false (부서 달라서)
    @Test
    void scenario2_author_sopQa_create_returnsFalse() {
        boolean result = evaluator.hasPermission(authenticated("author_it"), qaDoc, "CREATE");
        assertThat(result).isFalse();
    }

    // 시나리오 3a: Reviewer(department=null) → SOP/QC REVIEW = true
    @Test
    void scenario3a_reviewer_nullDept_sopQc_review_returnsTrue() {
        boolean result = evaluator.hasPermission(authenticated("reviewer_it"), qcDoc, "REVIEW");
        assertThat(result).isTrue();
    }

    // 시나리오 3b: Reviewer(department=null) → SOP/QA REVIEW = true
    @Test
    void scenario3b_reviewer_nullDept_sopQa_review_returnsTrue() {
        boolean result = evaluator.hasPermission(authenticated("reviewer_it"), qaDoc, "REVIEW");
        assertThat(result).isTrue();
    }

    // 시나리오 4: QA role → APPROVE = true
    @Test
    void scenario4_qa_sopQa_approve_returnsTrue() {
        boolean result = evaluator.hasPermission(authenticated("qa_it"), qaDoc, "APPROVE");
        assertThat(result).isTrue();
    }

    // 시나리오 5: 권한 없는 사용자 → false
    @Test
    void scenario5_noPermUser_allActions_returnFalse() {
        Authentication auth = authenticated("noperm_it");
        assertThat(evaluator.hasPermission(auth, qcDoc, "VIEW")).isFalse();
        assertThat(evaluator.hasPermission(auth, qcDoc, "CREATE")).isFalse();
        assertThat(evaluator.hasPermission(auth, qcDoc, "REVIEW")).isFalse();
        assertThat(evaluator.hasPermission(auth, qcDoc, "APPROVE")).isFalse();
        assertThat(evaluator.hasPermission(auth, qcDoc, "DOWNLOAD")).isFalse();
    }

    // hasPermission(Serializable targetId, String targetType) 오버로드 테스트
    @Test
    void hasPermission_withDocumentId_author_create_returnsTrue() {
        boolean result = evaluator.hasPermission(authenticated("author_it"), qcDoc.getId(), "DOCUMENT", "CREATE");
        assertThat(result).isTrue();
    }

    // 디버그: native query 직접 테스트
    @Test
    void debug_nativeQuery_directCall() {
        boolean result = permissionRepo.userHasPermission("author_it", sopCategoryId, "QC", "can_create");
        assertThat(result)
            .as("userHasPermission(author_it, %d, QC, can_create)", sopCategoryId)
            .isTrue();
    }

    // 디버그: evaluator.hasPermission Document 직접 테스트
    @Test
    void debug_evaluator_hasPermission_withDocumentObject() {
        // qcDoc 재조회 (em.clear() 이후)
        Document freshDoc = documentRepo.findById(qcDoc.getId()).orElseThrow();
        boolean result = evaluator.hasPermission(authenticated("author_it"), freshDoc, "CREATE");
        assertThat(result).isTrue();
    }

    // ---- 헬퍼 메서드 ----

    private void createDepartmentIfAbsent(String code, String name) {
        departmentRepo.findByDeptCode(code).orElseGet(() -> {
            Department d = new Department();
            d.setDeptCode(code);
            d.setPrimaryName(name);
            return departmentRepo.save(d);
        });
    }

    private Role createRole(String code, String name) {
        return roleRepo.findByRoleCode(code).orElseGet(() -> {
            Role r = new RoleBuilder(code, name).build();
            return roleRepo.save(r);
        });
    }

    private User createUser(String userId, String fullName, String department) {
        User u = new User();
        u.setUserId(userId);
        u.setFullName(fullName);
        u.setEmail(userId + "@test.lab");
        u.setDepartment(department);
        return userRepo.save(u);
    }

    private void assignRole(User user, Role role) {
        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(role);
        ur.setAssignedAt(OffsetDateTime.now());
        em.persist(ur);
    }

    private Document createDocument(String docNumber, Long categoryId, String department, Long createdBy) {
        Document d = new Document();
        d.setDocNumber(docNumber);
        d.setCategoryId(categoryId);
        d.setDepartment(department);
        d.setTitle("Test " + docNumber);
        d.setOwnerId(createdBy);
        d.setConfidential(false);
        d.setCreatedBy(createdBy);
        return documentRepo.save(d);
    }

    // Role은 @Audited이라 직접 생성이 복잡해서 빌더 헬퍼 사용
    private static class RoleBuilder {
        private final String code;
        private final String name;
        RoleBuilder(String code, String name) { this.code = code; this.name = name; }
        Role build() {
            try {
                var constructor = Role.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                Role r = constructor.newInstance();
                setField(r, "roleCode", code);
                setField(r, "roleName", name);
                setField(r, "system", false);
                setField(r, "createdAt", OffsetDateTime.now());
                return r;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        private void setField(Object obj, String field, Object value) throws Exception {
            var f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(obj, value);
        }
    }
}
