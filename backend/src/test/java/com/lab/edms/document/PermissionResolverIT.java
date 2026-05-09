package com.lab.edms.document;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.category.DocumentCategory;
import com.lab.edms.category.DocumentCategoryRepository;
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

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class PermissionResolverIT {

    @Autowired PermissionResolver permissionResolver;
    @Autowired UserRepository userRepo;
    @Autowired RoleRepository roleRepo;
    @Autowired DocumentCategoryRepository catRepo;
    @Autowired PermissionRepository permRepo;
    @Autowired EntityManager em;

    private Long sopCategoryId;
    private Long methodCategoryId;
    private Role readerRole;

    @BeforeEach
    void setUp() {
        sopCategoryId = catRepo.findByCategoryCode("SOP").orElseThrow().getId();
        methodCategoryId = catRepo.findByCategoryCode("METHOD").orElseThrow().getId();
        readerRole = roleRepo.findByRoleCode("READER").orElseThrow();
    }

    private User createUser(String userId, String dept) {
        User user = new User();
        user.setUserId(userId);
        user.setFullName("Test " + userId);
        user.setEmail(userId + "@lab.test");
        user.setDepartment(dept);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("irrelevant");
        user.setForceChangePw(false);
        userRepo.save(user);

        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(readerRole);
        ur.setAssignedAt(OffsetDateTime.now());
        em.persist(ur);
        em.flush();
        em.clear();
        return userRepo.findByUserId(userId).orElseThrow();
    }

    private void grantViewPermission(Long roleId, Long categoryId, String dept) {
        Permission p = new Permission();
        p.setRoleId(roleId);
        p.setCategoryId(categoryId);
        p.setDepartment(dept);
        p.setCanView(true);
        permRepo.save(p);
        em.flush();
        em.clear();
    }

    @Test
    void userWithSopQcCanView_seesOnlySopQcDocs() {
        String userId = "perm_user_" + System.nanoTime();
        createUser(userId, "QC");
        grantViewPermission(readerRole.getId(), sopCategoryId, "QC");

        VisibilityScope scope = permissionResolver.resolveViewable(userId);

        assertThat(scope.categoryIds()).contains(sopCategoryId);
        assertThat(scope.deptCodes()).contains("QC");
        assertThat(scope.allDepts()).isFalse();
    }

    @Test
    void userWithNullDeptPermission_hasAllDeptsFlag() {
        String userId = "perm_user2_" + System.nanoTime();
        createUser(userId, "QC");
        // Grant org-wide SOP permission (dept = null)
        grantViewPermission(readerRole.getId(), sopCategoryId, null);

        VisibilityScope scope = permissionResolver.resolveViewable(userId);

        assertThat(scope.categoryIds()).contains(sopCategoryId);
        assertThat(scope.allDepts()).isTrue();
    }

    @Test
    void canCreate_returnsFalse_forUnauthorizedCategory() {
        String userId = "perm_user3_" + System.nanoTime();
        createUser(userId, "QC");
        // Only grant view for SOP, not create
        grantViewPermission(readerRole.getId(), sopCategoryId, "QC");

        boolean result = permissionResolver.canCreate(userId, sopCategoryId, "QC");

        assertThat(result).isFalse();
    }

    @Test
    void canCreate_returnsTrue_whenCreatePermissionGranted() {
        String userId = "perm_user4_" + System.nanoTime();
        createUser(userId, "QC");

        Permission p = new Permission();
        p.setRoleId(readerRole.getId());
        p.setCategoryId(sopCategoryId);
        p.setDepartment("QC");
        p.setCanView(true);
        p.setCanCreate(true);
        permRepo.save(p);
        em.flush();
        em.clear();

        boolean result = permissionResolver.canCreate(userId, sopCategoryId, "QC");

        assertThat(result).isTrue();
    }

    @Test
    void canCreate_withNullDeptPermission_matchesAnyDept() {
        String userId = "perm_user5_" + System.nanoTime();
        createUser(userId, "QC");

        Permission p = new Permission();
        p.setRoleId(readerRole.getId());
        p.setCategoryId(sopCategoryId);
        p.setDepartment(null); // org-wide
        p.setCanView(true);
        p.setCanCreate(true);
        permRepo.save(p);
        em.flush();
        em.clear();

        assertThat(permissionResolver.canCreate(userId, sopCategoryId, "QC")).isTrue();
        assertThat(permissionResolver.canCreate(userId, sopCategoryId, "RD")).isTrue();
    }

    @Test
    void noPermissions_returnsFalse() {
        String userId = "perm_user6_" + System.nanoTime();
        createUser(userId, "QC");
        // No permissions at all
        boolean result = permissionResolver.canCreate(userId, sopCategoryId, "QC");
        assertThat(result).isFalse();
    }
}
