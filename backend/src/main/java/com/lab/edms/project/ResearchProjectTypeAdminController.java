package com.lab.edms.project;

import com.lab.edms.project.dto.ProjectTypeDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/research-project-types")
@PreAuthorize("hasAnyRole('QA','ADMIN')")
public class ResearchProjectTypeAdminController {

    private final ResearchProjectService service;

    public ResearchProjectTypeAdminController(ResearchProjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProjectTypeDto> list() {
        return service.listTypes().stream().map(ProjectTypeDto::from).toList();
    }
}
