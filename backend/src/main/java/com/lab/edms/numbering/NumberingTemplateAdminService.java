package com.lab.edms.numbering;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.numbering.dto.*;
import com.lab.edms.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class NumberingTemplateAdminService {

    private final NumberingTemplateRepository templateRepo;
    private final DocumentCategoryRepository categoryRepo;
    private final NumberingService numberingService;
    private final AuditService auditService;
    private final UserRepository userRepo;

    public NumberingTemplateAdminService(NumberingTemplateRepository templateRepo,
                                          DocumentCategoryRepository categoryRepo,
                                          NumberingService numberingService,
                                          AuditService auditService,
                                          UserRepository userRepo) {
        this.templateRepo = templateRepo;
        this.categoryRepo = categoryRepo;
        this.numberingService = numberingService;
        this.auditService = auditService;
        this.userRepo = userRepo;
    }

    public List<NumberingTemplateDto> findAll() {
        return templateRepo.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public NumberingTemplateDto create(UpsertNumberingTemplateRequest req, String actorUserId) {
        validateScope(req.counterScope());
        Long actorId = resolveActorId(actorUserId);
        NumberingTemplate t = new NumberingTemplate();
        t.setCategoryId(req.categoryId());
        t.setFormatPattern(req.formatPattern());
        t.setCounterScope(req.counterScope());
        t.setCreatedBy(actorId);
        t.setUpdatedBy(actorId);
        NumberingTemplate saved = templateRepo.save(t);
        auditService.log(new AuditEvent(actorUserId, AuditAction.NUMBERING_TEMPLATE_CREATED,
                "numbering_template", String.valueOf(saved.getId()), null, saved.getFormatPattern(),
                null, null, OffsetDateTime.now(ZoneOffset.UTC)));
        return toDto(saved);
    }

    @Transactional
    public NumberingTemplateDto update(Long id, UpsertNumberingTemplateRequest req, String actorUserId) {
        validateScope(req.counterScope());
        Long actorId = resolveActorId(actorUserId);
        NumberingTemplate t = templateRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Template not found: " + id));
        String before = t.getFormatPattern();
        t.setFormatPattern(req.formatPattern());
        t.setCounterScope(req.counterScope());
        t.setUpdatedBy(actorId);
        NumberingTemplate saved = templateRepo.save(t);
        auditService.log(new AuditEvent(actorUserId, AuditAction.NUMBERING_TEMPLATE_UPDATED,
                "numbering_template", String.valueOf(id), before, saved.getFormatPattern(),
                null, null, OffsetDateTime.now(ZoneOffset.UTC)));
        return toDto(saved);
    }

    public NumberingPreviewResponse preview(NumberingPreviewRequest req) {
        var result = numberingService.peek(req.categoryId(),
                new NumberingService.IssueContext(req.department(), req.projectCode()));
        return new NumberingPreviewResponse(result.docNumber(), result.seq());
    }

    private Long resolveActorId(String userId) {
        return userRepo.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "actor not found"))
                .getId();
    }

    private void validateScope(String scope) {
        if (!List.of("PER_DEPT", "PER_PRODUCT", "PER_YEAR", "GLOBAL").contains(scope)) {
            throw new IllegalArgumentException("Invalid counter_scope: " + scope);
        }
    }

    private NumberingTemplateDto toDto(NumberingTemplate t) {
        String code = categoryRepo.findById(t.getCategoryId())
                .map(c -> c.getCategoryCode()).orElse("?");
        return new NumberingTemplateDto(t.getId(), t.getCategoryId(), code,
                t.getFormatPattern(), t.getCounterScope(), t.getUpdatedAt());
    }
}
