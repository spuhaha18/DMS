package com.lab.edms.project;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.document.DocumentFileRepository;
import com.lab.edms.document.DocumentFile;
import com.lab.edms.project.dto.CreateProjectRequest;
import com.lab.edms.storage.RetentionResolver;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@Transactional
public class ResearchProjectService {

    private static final int PAGE_SIZE = 200;

    private final ResearchProjectRepository projects;
    private final ResearchProjectTypeRepository types;
    private final DocumentFileRepository files;
    private final RetentionExtensionOutboxRepository outbox;
    private final RetentionResolver retentionResolver;
    private final AuditService auditService;
    private final UserRepository users;
    private final Clock clock;

    public ResearchProjectService(ResearchProjectRepository projects,
                                  ResearchProjectTypeRepository types,
                                  DocumentFileRepository files,
                                  RetentionExtensionOutboxRepository outbox,
                                  RetentionResolver retentionResolver,
                                  AuditService auditService,
                                  UserRepository users,
                                  Clock clock) {
        this.projects = projects;
        this.types = types;
        this.files = files;
        this.outbox = outbox;
        this.retentionResolver = retentionResolver;
        this.auditService = auditService;
        this.users = users;
        this.clock = clock;
    }

    public List<ResearchProjectType> listTypes() {
        return types.findByActiveTrue();
    }

    public List<ResearchProject> search(ResearchProjectStatus status, String typeCode) {
        return projects.search(status, typeCode);
    }

    public ResearchProject create(CreateProjectRequest req, Authentication auth) {
        User actor = requireUser(auth);
        ResearchProjectType type = types.findById(req.typeCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown type_code: " + req.typeCode()));

        ResearchProject p = new ResearchProject();
        p.setProjectCode(req.projectCode());
        p.setProjectName(req.projectName());
        p.setType(type);
        p.setCreatedBy(actor);
        p.setUpdatedBy(actor);
        projects.save(p);

        auditService.log(AuditEvent.of(auth.getName(), AuditAction.RESEARCH_PROJECT_REGISTERED)
                .entity("research_project", req.projectCode())
                .after("{\"type_code\":\"" + req.typeCode() + "\"}")
                .build());
        return p;
    }

    public ResearchProject approve(String code, LocalDate approvalDate, Authentication auth) {
        User actor = requireUser(auth);
        ResearchProject p = requireProject(code);
        p.approve(approvalDate, actor, OffsetDateTime.now(clock));
        enqueueRetentionExtension(p, approvalDate);
        auditService.log(AuditEvent.of(auth.getName(), AuditAction.RESEARCH_PROJECT_APPROVED)
                .entity("research_project", code)
                .after("{\"approval_date\":\"" + approvalDate + "\"}")
                .build());
        return p;
    }

    public ResearchProject terminate(String code, LocalDate terminationDate, Authentication auth) {
        User actor = requireUser(auth);
        ResearchProject p = requireProject(code);
        p.terminate(terminationDate, actor, OffsetDateTime.now(clock));
        enqueueRetentionExtension(p, terminationDate);
        auditService.log(AuditEvent.of(auth.getName(), AuditAction.RESEARCH_PROJECT_TERMINATED)
                .entity("research_project", code)
                .after("{\"termination_date\":\"" + terminationDate + "\"}")
                .build());
        return p;
    }

    private void enqueueRetentionExtension(ResearchProject p, LocalDate startDate) {
        int years = retentionResolver.resolveYears(p);
        Instant until = startDate.plusYears(years).atStartOfDay(ZoneOffset.UTC).toInstant();
        int pageNum = 0;
        while (true) {
            Page<DocumentFile> page = files.findByProjectCode(
                    p.getProjectCode(),
                    PageRequest.of(pageNum, PAGE_SIZE, Sort.by("id")));
            for (DocumentFile f : page.getContent()) {
                outbox.save(RetentionExtensionOutbox.pending(
                        p.getProjectCode(), f.getId(), f.getMinioBucket(), f.getMinioKey(), until));
            }
            if (!page.hasNext()) break;
            pageNum++;
        }
    }

    private ResearchProject requireProject(String code) {
        return projects.findById(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Research project not found: " + code));
    }

    private User requireUser(Authentication auth) {
        return users.findByUserId(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "User not found: " + auth.getName()));
    }
}
