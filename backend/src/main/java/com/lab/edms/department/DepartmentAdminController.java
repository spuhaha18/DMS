package com.lab.edms.department;

import com.lab.edms.department.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DepartmentAdminController {

    private final DepartmentAdminService service;

    public DepartmentAdminController(DepartmentAdminService service) {
        this.service = service;
    }

    @GetMapping("/departments")
    public List<DepartmentDto> listActive() {
        return service.findAllActive();
    }

    @GetMapping("/admin/departments")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DepartmentDto> listAll() {
        return service.findAll();
    }

    @PostMapping("/admin/departments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentDto> create(@Valid @RequestBody UpsertDepartmentRequest req,
                                                  @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(201).body(service.create(req, user.getUsername()));
    }

    @PutMapping("/admin/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DepartmentDto update(@PathVariable Long id,
                                  @Valid @RequestBody UpsertDepartmentRequest req,
                                  @AuthenticationPrincipal UserDetails user) {
        return service.update(id, req, user.getUsername());
    }

    @DeleteMapping("/admin/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id,
                                             @AuthenticationPrincipal UserDetails user) {
        service.deactivate(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/departments/{id}/aliases")
    @PreAuthorize("hasRole('ADMIN')")
    public DepartmentDto addAlias(@PathVariable Long id,
                                    @Valid @RequestBody UpsertAliasRequest req,
                                    @AuthenticationPrincipal UserDetails user) {
        return service.addAlias(id, req, user.getUsername());
    }

    @DeleteMapping("/admin/departments/{id}/aliases/{aliasId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeAlias(@PathVariable Long id,
                                              @PathVariable Long aliasId,
                                              @AuthenticationPrincipal UserDetails user) {
        service.removeAlias(id, aliasId, user.getUsername());
        return ResponseEntity.noContent().build();
    }
}
