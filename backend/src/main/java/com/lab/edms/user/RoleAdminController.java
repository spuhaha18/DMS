package com.lab.edms.user;

import com.lab.edms.user.dto.RoleDto;
import com.lab.edms.user.dto.UpdateRoleRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/roles")
@PreAuthorize("hasRole('ADMIN')")
public class RoleAdminController {

    private final RoleAdminService svc;

    public RoleAdminController(RoleAdminService svc) { this.svc = svc; }

    @GetMapping
    public List<RoleDto> list() { return svc.list(); }

    @PutMapping("/{roleId}")
    public RoleDto update(@PathVariable Long roleId,
                          @RequestBody @Valid UpdateRoleRequest req,
                          Authentication auth, HttpServletRequest http) {
        return svc.update(roleId, req, auth.getName(), http.getRemoteAddr());
    }
}
