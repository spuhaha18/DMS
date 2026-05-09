package com.lab.edms.numbering;

import com.lab.edms.numbering.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class NumberingTemplateAdminController {

    private final NumberingTemplateAdminService service;

    public NumberingTemplateAdminController(NumberingTemplateAdminService service) {
        this.service = service;
    }

    @GetMapping("/numbering-templates")
    public List<NumberingTemplateDto> listAll() {
        return service.findAll();
    }

    @PostMapping("/numbering-templates")
    public ResponseEntity<NumberingTemplateDto> create(@Valid @RequestBody UpsertNumberingTemplateRequest req,
                                                        @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(201).body(service.create(req, user.getUsername()));
    }

    @PutMapping("/numbering-templates/{id}")
    public NumberingTemplateDto update(@PathVariable Long id,
                                        @Valid @RequestBody UpsertNumberingTemplateRequest req,
                                        @AuthenticationPrincipal UserDetails user) {
        return service.update(id, req, user.getUsername());
    }

    @PostMapping("/numbering-counters/preview")
    public NumberingPreviewResponse preview(@RequestBody NumberingPreviewRequest req) {
        return service.preview(req);
    }
}
