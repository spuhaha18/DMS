package com.lab.edms.category;

import com.lab.edms.category.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
                                               @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(201).body(service.create(req, user.getUsername()));
    }

    @PutMapping("/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryDto update(@PathVariable Long id,
                               @Valid @RequestBody UpsertCategoryRequest req,
                               @AuthenticationPrincipal UserDetails user) {
        return service.update(id, req, user.getUsername());
    }
}
