package com.lab.edms.workflow;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.document.Document;
import com.lab.edms.permission.Permission;
import com.lab.edms.permission.PermissionRepository;
import com.lab.edms.user.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AssigneeResolverIT — AssigneeResolver.resolveAll() 검증.
 *
 * 시나리오:
 * 1. SOP 템플릿 step1(REVIEW, REVIEWER, min_signers=1, auto_assign=true) → 후보 3명 반환
 * 2. step3(APPROVAL, QA, qa_required=true, auto_assign=true) → QA 역할 후보 2명 반환
 * 3. 후보 0명 시 IllegalStateException("결재 가능 사용자 부족") 발생
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class AssigneeResolverIT {

    @Autowired AssigneeResolver assigneeResolver;
    @Autowired UserRepository userRepo;
    @Autowired RoleRepository roleRepo;
    @Autowired DocumentCategoryRepository catRepo;
    @Autowired PermissionRepository permRepo;
    @Autowired EntityManager em;

    private Long sopCategoryId;
    private Role reviewerRole;
    private Role qaRole;

    @BeforeEach
    void setUp() {
        sopCategoryId = catRepo.findByCategoryCode("SOP").orElseThrow().getId();
        reviewerRole = roleRepo.findByRoleCode("REVIEWER").orElseThrow();
        qaRole = roleRepo.findByRoleCode("QA").orElseThrow();
    }

    // ── 헬퍼 메서드 ──

    private User createUser(String userId, String dept) {
        User user = new User();
        user.setUserId(userId);
        user.setFullName("테스트 " + userId);
        user.setEmail(userId + "@lab.test");
        user.setDepartment(dept);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("irrelevant");
        user.setForceChangePw(false);
        return userRepo.save(user);
    }

    private void assignRole(User user, Role role) {
        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(role);
        ur.setAssignedAt(java.time.OffsetDateTime.now());
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

    private Document makeDocument(String dept) {
        Document doc = new Document();
        doc.setCategoryId(sopCategoryId);
        doc.setDepartment(dept);
        // 다른 필드는 AssigneeResolver가 categoryId + department만 사용하므로 최소값 설정
        doc.setDocNumber("TST-" + System.nanoTime());
        doc.setTitle("테스트 문서");
        doc.setOwnerId(1L);
        doc.setCreatedBy(1L);
        doc.setConfidential(false);
        return doc;
    }

    private WorkflowTemplateStep makeStep(int order, String stepType, String roleCode,
                                           int minSigners, boolean autoAssign, boolean qaRequired) {
        WorkflowTemplateStep step = new WorkflowTemplateStep();
        step.setStepOrder(order);
        step.setStepType(stepType);
        step.setRoleCode(roleCode);
        step.setMinSigners(minSigners);
        step.setAutoAssign(autoAssign);
        step.setQaRequired(qaRequired);
        step.setParallel(false);
        return step;
    }

    // ── 테스트 1: REVIEWER 3명 → step1 후보 3명 반환 ──

    @Test
    void step1_reviewer_3명_자동배정_후보_3명_반환() {
        // REVIEWER 3명 생성 + SOP/RD 부서 can_review 권한 부여
        User r1 = createUser("rev_it_01", "RD");
        User r2 = createUser("rev_it_02", "RD");
        User r3 = createUser("rev_it_03", "RD");
        assignRole(r1, reviewerRole);
        assignRole(r2, reviewerRole);
        assignRole(r3, reviewerRole);
        em.flush();
        em.clear();

        grantPermission(reviewerRole.getId(), sopCategoryId, "RD", true, false);
        em.flush();
        em.clear();

        WorkflowTemplateStep step = makeStep(1, "REVIEW", "REVIEWER", 1, true, false);
        Document doc = makeDocument("RD");

        List<List<AssigneeRef>> result = assigneeResolver.resolveAll(
                List.of(step), doc, Map.of(), "admin", Instant.now());

        assertThat(result).hasSize(1);
        // 3명이 REVIEWER 역할 + SOP/RD can_review 권한 보유
        assertThat(result.get(0)).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.get(0)).extracting(AssigneeRef::userIdString)
                .contains("rev_it_01", "rev_it_02", "rev_it_03");
    }

    // ── 테스트 2: QA 2명 → step3 후보 2명 반환 (qa_required=true) ──

    @Test
    void step3_qa_required_2명_자동배정_후보_2명_반환() {
        // QA 역할 사용자 2명 생성 + SOP/QC can_approve 권한 부여
        User qa1 = createUser("qa_it_01", "QC");
        User qa2 = createUser("qa_it_02", "QC");
        assignRole(qa1, qaRole);
        assignRole(qa2, qaRole);
        em.flush();
        em.clear();

        grantPermission(qaRole.getId(), sopCategoryId, "QC", false, true);
        em.flush();
        em.clear();

        // qa_required=true → role_code를 QA로 강제 필터링
        WorkflowTemplateStep step = makeStep(3, "APPROVAL", "QA", 1, true, true);
        Document doc = makeDocument("QC");

        List<List<AssigneeRef>> result = assigneeResolver.resolveAll(
                List.of(step), doc, Map.of(), "admin", Instant.now());

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.get(0)).extracting(AssigneeRef::userIdString)
                .contains("qa_it_01", "qa_it_02");
    }

    // ── 테스트 3: 후보 0명 시 IllegalStateException ──

    @Test
    void 후보_0명일때_IllegalStateException_결재가능사용자부족() {
        // 권한을 부여하지 않은 상태 → 후보 없음
        WorkflowTemplateStep step = makeStep(1, "REVIEW", "REVIEWER", 1, true, false);
        // 아무 권한도 없는 카테고리/부서 조합 사용
        Document doc = makeDocument("NODEPT_" + System.nanoTime());

        assertThatThrownBy(() ->
                assigneeResolver.resolveAll(List.of(step), doc, Map.of(), "admin", Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결재 가능 사용자 부족");
    }
}
