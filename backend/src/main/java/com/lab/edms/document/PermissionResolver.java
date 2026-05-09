package com.lab.edms.document;

import com.lab.edms.permission.Permission;
import com.lab.edms.permission.PermissionRepository;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionResolver {

    private final UserRepository userRepo;
    private final PermissionRepository permRepo;

    public PermissionResolver(UserRepository userRepo, PermissionRepository permRepo) {
        this.userRepo = userRepo;
        this.permRepo = permRepo;
    }

    @Transactional(readOnly = true)
    public VisibilityScope resolveViewable(String userId) {
        User user = userRepo.findByUserIdWithRoles(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));

        Set<Long> roleIds = user.getRoles().stream()
                .map(ur -> ur.getRole().getId())
                .collect(Collectors.toSet());

        if (roleIds.isEmpty()) {
            return new VisibilityScope(Collections.emptySet(), Collections.emptySet(), false);
        }

        // Gather all permissions for all roles (can_view = true)
        Set<Long> categoryIds = new LinkedHashSet<>();
        Set<String> deptCodes = new LinkedHashSet<>();
        boolean allDepts = false;

        for (Long roleId : roleIds) {
            List<Permission> perms = permRepo.findByRoleId(roleId);
            for (Permission p : perms) {
                if (p.isCanView()) {
                    categoryIds.add(p.getCategoryId());
                    if (p.getDepartment() == null) {
                        allDepts = true;
                    } else {
                        deptCodes.add(p.getDepartment());
                    }
                }
            }
        }

        return new VisibilityScope(categoryIds, deptCodes, allDepts);
    }

    @Transactional(readOnly = true)
    public boolean canCreate(String userId, Long categoryId, String deptCode) {
        User user = userRepo.findByUserIdWithRoles(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));

        Set<Long> roleIds = user.getRoles().stream()
                .map(ur -> ur.getRole().getId())
                .collect(Collectors.toSet());

        for (Long roleId : roleIds) {
            List<Permission> perms = permRepo.findByRoleIdAndCategoryId(roleId, categoryId);
            for (Permission p : perms) {
                if (p.isCanCreate()) {
                    // department null = all departments
                    if (p.getDepartment() == null || p.getDepartment().equals(deptCode)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns the dept codes where the authenticated user can create docs for the given category.
     * If any permission has department=null, returns null to signal "all departments allowed".
     */
    @Transactional(readOnly = true)
    public List<String> creatableDepts(String userId, Long categoryId) {
        User user = userRepo.findByUserIdWithRoles(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));

        Set<Long> roleIds = user.getRoles().stream()
                .map(ur -> ur.getRole().getId())
                .collect(Collectors.toSet());

        Set<String> depts = new LinkedHashSet<>();
        boolean anyNull = false;

        for (Long roleId : roleIds) {
            List<Permission> perms = permRepo.findByRoleIdAndCategoryId(roleId, categoryId);
            for (Permission p : perms) {
                if (p.isCanCreate()) {
                    if (p.getDepartment() == null) {
                        anyNull = true;
                    } else {
                        depts.add(p.getDepartment());
                    }
                }
            }
        }

        // If any org-wide permission found, signal null to caller
        if (anyNull) return null;
        return new ArrayList<>(depts);
    }
}
