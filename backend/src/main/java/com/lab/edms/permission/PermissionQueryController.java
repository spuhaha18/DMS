package com.lab.edms.permission;

import com.lab.edms.department.Department;
import com.lab.edms.department.DepartmentRepository;
import com.lab.edms.document.PermissionResolver;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionQueryController {

    private final PermissionResolver permissionResolver;
    private final DepartmentRepository deptRepo;

    public PermissionQueryController(PermissionResolver permissionResolver,
                                     DepartmentRepository deptRepo) {
        this.permissionResolver = permissionResolver;
        this.deptRepo = deptRepo;
    }

    /**
     * GET /api/v1/permissions/creatable?categoryId={id}
     * Returns dept_codes where the authenticated user can create documents for the given category.
     * If user has org-wide permission (dept=NULL), returns all active dept codes.
     */
    @GetMapping("/creatable")
    public List<String> creatableDepts(
            @RequestParam Long categoryId,
            Authentication auth) {
        List<String> depts = permissionResolver.creatableDepts(auth.getName(), categoryId);
        if (depts == null) {
            // org-wide permission: return all active departments
            return deptRepo.findAllByActiveTrue().stream()
                    .map(Department::getDeptCode)
                    .collect(Collectors.toList());
        }
        return depts;
    }
}
