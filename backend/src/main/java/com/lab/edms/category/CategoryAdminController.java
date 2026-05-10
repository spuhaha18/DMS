package com.lab.edms.category;

import com.lab.edms.category.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CategoryAdminController {

    private final CategoryAdminService service;

    public CategoryAdminController(CategoryAdminService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public List<CategoryDto> listActive() {
        return service.findAllActive();
    }

    @GetMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CategoryDto> listAll() {
        return service.findAll();
    }

    @PostMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryDto> create(@Valid @RequestBody UpsertCategoryRequest req,
                                               Authentication auth) {
        return ResponseEntity.status(201).body(service.create(req, auth.getName()));
    }

    @PutMapping("/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryDto update(@PathVariable Long id,
                               @Valid @RequestBody UpsertCategoryRequest req,
                               Authentication auth) {
        return service.update(id, req, auth.getName());
    }
}
