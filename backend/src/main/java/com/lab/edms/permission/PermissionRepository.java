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
}
