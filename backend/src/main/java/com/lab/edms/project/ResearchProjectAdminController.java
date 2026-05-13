package com.lab.edms.project;

import com.lab.edms.project.dto.ApproveProjectRequest;
import com.lab.edms.project.dto.CreateProjectRequest;
import com.lab.edms.project.dto.ProjectDto;
import com.lab.edms.project.dto.TerminateProjectRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/research-projects")
@PreAuthorize("hasAnyRole('QA','ADMIN')")
public class ResearchProjectAdminController {

    private final ResearchProjectService service;

    public ResearchProjectAdminController(ResearchProjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProjectDto> list(@RequestParam(required = false) ResearchProjectStatus status,
                                 @RequestParam(required = false) String typeCode) {
        return service.search(status, typeCode).stream().map(ProjectDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto create(@Valid @RequestBody CreateProjectRequest req, Authentication auth) {
        return ProjectDto.from(service.create(req, auth));
    }

    @PostMapping("/{code}/approve")
    public ProjectDto approve(@PathVariable String code,
                              @Valid @RequestBody ApproveProjectRequest req,
                              Authentication auth) {
        return ProjectDto.from(service.approve(code, req.approvalDate(), auth));
    }

    @PostMapping("/{code}/terminate")
    public ProjectDto terminate(@PathVariable String code,
                                @Valid @RequestBody TerminateProjectRequest req,
                                Authentication auth) {
        return ProjectDto.from(service.terminate(code, req.terminationDate(), auth));
    }
}
