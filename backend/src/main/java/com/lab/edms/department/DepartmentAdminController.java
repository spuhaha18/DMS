package com.lab.edms.department;

import com.lab.edms.department.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
                                                  Authentication auth) {
        return ResponseEntity.status(201).body(service.create(req, auth.getName()));
    }

    @PutMapping("/admin/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DepartmentDto update(@PathVariable Long id,
                                  @Valid @RequestBody UpsertDepartmentRequest req,
                                  Authentication auth) {
        return service.update(id, req, auth.getName());
    }

    @DeleteMapping("/admin/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id,
                                             Authentication auth) {
        service.deactivate(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/departments/{id}/aliases")
    @PreAuthorize("hasRole('ADMIN')")
    public DepartmentDto addAlias(@PathVariable Long id,
                                    @Valid @RequestBody UpsertAliasRequest req,
                                    Authentication auth) {
        return service.addAlias(id, req, auth.getName());
    }

    @DeleteMapping("/admin/departments/{id}/aliases/{aliasId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeAlias(@PathVariable Long id,
                                              @PathVariable Long aliasId,
                                              Authentication auth) {
        service.removeAlias(id, aliasId, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
