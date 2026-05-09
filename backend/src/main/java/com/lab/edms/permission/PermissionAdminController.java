package com.lab.edms.permission;

import com.lab.edms.permission.dto.PermissionDto;
import com.lab.edms.permission.dto.UpsertPermissionRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/permissions")
@PreAuthorize("hasRole('ADMIN')")
public class PermissionAdminController {

    private final PermissionAdminService svc;

    public PermissionAdminController(PermissionAdminService svc) { this.svc = svc; }

    @GetMapping
    public List<PermissionDto> list(@RequestParam(required = false) Long role_id,
                                    @RequestParam(required = false) Long category_id) {
        return svc.list(role_id, category_id);
    }

    @PutMapping
    public PermissionDto upsert(@RequestBody @Valid UpsertPermissionRequest req,
                                Authentication auth, HttpServletRequest http) {
        return svc.upsert(req, auth.getName(), http.getRemoteAddr());
    }

    @DeleteMapping("/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long permissionId,
                       Authentication auth, HttpServletRequest http) {
        svc.delete(permissionId, auth.getName(), http.getRemoteAddr());
    }
}
