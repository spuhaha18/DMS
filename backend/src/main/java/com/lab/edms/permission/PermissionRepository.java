package com.lab.edms.permission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByRoleIdAndCategoryIdAndDepartment(
            Long roleId, Long categoryId, String department);

    @Query("SELECT p FROM Permission p WHERE p.roleId = :roleId AND p.categoryId = :categoryId AND p.department IS NULL")
    Optional<Permission> findByRoleIdAndCategoryIdAndDepartmentIsNull(
            @Param("roleId") Long roleId, @Param("categoryId") Long categoryId);

    List<Permission> findByRoleId(Long roleId);
    List<Permission> findByCategoryId(Long categoryId);
    List<Permission> findByRoleIdAndCategoryId(Long roleId, Long categoryId);

    @Query(value = """
      SELECT EXISTS(
        SELECT 1 FROM users u
        JOIN user_roles ur ON ur.user_id = u.id
        JOIN permissions p ON p.role_id = ur.role_id
        WHERE u.user_id = :userId
          AND p.category_id = :categoryId
          AND (p.department IS NULL OR p.department = :department)
          AND CASE :flag
                WHEN 'can_review'     THEN p.can_review
                WHEN 'can_approve'    THEN p.can_approve
                WHEN 'can_retire'     THEN p.can_retire
                WHEN 'can_edit_draft' THEN p.can_edit_draft
                WHEN 'can_view'       THEN p.can_view
                WHEN 'can_download'   THEN p.can_download
                WHEN 'can_create'     THEN p.can_create
              END = TRUE
      )
    """, nativeQuery = true)
    boolean userHasPermission(@Param("userId") String userId,
                              @Param("categoryId") Long categoryId,
                              @Param("department") String department,
                              @Param("flag") String flag);
}
