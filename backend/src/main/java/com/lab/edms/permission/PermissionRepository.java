package com.lab.edms.permission;

import com.lab.edms.user.User;
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

    /**
     * 특정 role_code, category, department, 권한 flag를 가진 활성 사용자 id 목록 조회.
     * AssigneeResolver에서 자동 배정 후보 풀 계산에 사용.
     * native query 결과를 User 엔티티로 직접 매핑하면 @Audited 로 인한 컨버터 문제가
     * 발생하므로, id만 반환하고 호출 측에서 UserRepository로 로드한다.
     */
    @Query(value = """
      SELECT DISTINCT u.id
        FROM users u
        JOIN user_roles ur ON ur.user_id = u.id
        JOIN roles r ON r.id = ur.role_id
        JOIN permissions p ON p.role_id = r.id
       WHERE r.role_code = :roleCode
         AND p.category_id = :categoryId
         AND (p.department IS NULL OR p.department = :department)
         AND CASE :flag
               WHEN 'can_review'  THEN p.can_review
               WHEN 'can_approve' THEN p.can_approve
               WHEN 'can_retire'  THEN p.can_retire
             END = TRUE
         AND u.status = 'ACTIVE'
    """, nativeQuery = true)
    List<Long> findUserIdsByRoleCodeAndPermission(
            @Param("roleCode") String roleCode,
            @Param("categoryId") Long categoryId,
            @Param("department") String department,
            @Param("flag") String flag);
}
