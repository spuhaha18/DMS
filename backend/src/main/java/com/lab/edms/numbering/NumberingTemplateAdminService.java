package com.lab.edms.numbering;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.numbering.dto.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class NumberingTemplateAdminService {

    private final NumberingTemplateRepository templateRepo;
    private final DocumentCategoryRepository categoryRepo;
    private final NumberingService numberingService;
    private final AuditService auditService;

    public NumberingTemplateAdminService(NumberingTemplateRepository templateRepo,
                                          DocumentCategoryRepository categoryRepo,
                                          NumberingService numberingService,
                                          AuditService auditService) {
        this.templateRepo = templateRepo;
        this.categoryRepo = categoryRepo;
        this.numberingService = numberingService;
        this.auditService = auditService;
    }

    public List<NumberingTemplateDto> findAll() {
        return templateRepo.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public NumberingTemplateDto create(UpsertNumberingTemplateRequest req, String actorUserId) {
        validateScope(req.counterScope());
        NumberingTemplate t = new NumberingTemplate();
        t.setCategoryId(req.categoryId());
        t.setFormatPattern(req.formatPattern());
        t.setCounterScope(req.counterScope());
        t.setCreatedBy(0L); // system placeholder
        t.setUpdatedBy(0L);
        NumberingTemplate saved = templateRepo.save(t);
        auditService.log(new AuditEvent(actorUserId, AuditAction.NUMBERING_TEMPLATE_CREATED,
                "numbering_template", String.valueOf(saved.getId()), null, saved.getFormatPattern(),
                null, null, OffsetDateTime.now(ZoneOffset.UTC)));
        return toDto(saved);
    }

    @Transactional
    public NumberingTemplateDto update(Long id, UpsertNumberingTemplateRequest req, String actorUserId) {
        validateScope(req.counterScope());
        NumberingTemplate t = templateRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Template not found: " + id));
        String before = t.getFormatPattern();
        t.setFormatPattern(req.formatPattern());
        t.setCounterScope(req.counterScope());
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
