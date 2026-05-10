package com.lab.edms.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.category.dto.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class CategoryAdminService {

    private final DocumentCategoryRepository repo;
    private final AuditService auditService;
    private final ObjectMapper json = new ObjectMapper();

    public CategoryAdminService(DocumentCategoryRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    private String jsonOf(Object o) {
        try { return json.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }

    public List<CategoryDto> findAll() {
        return repo.findAll().stream().map(this::toDto).toList();
    }

    public List<CategoryDto> findAllActive() {
        return repo.findAll().stream().filter(DocumentCategory::isActive).map(this::toDto).toList();
    }

    @Transactional
    public CategoryDto create(UpsertCategoryRequest req, String actorUserId) {
        DocumentCategory c = new DocumentCategory();
        c.setCategoryCode(req.categoryCode().toUpperCase());
        c.setCategoryName(req.categoryName());
        c.setDescription(req.description());
        c.setReviewPeriodMonths(req.reviewPeriodMonths() > 0 ? req.reviewPeriodMonths() : 24);
        c.setQaMandatory(req.qaMandatory());
        if (req.active() != null) c.setActive(req.active());
        DocumentCategory saved = repo.save(c);
        auditService.log(new AuditEvent(actorUserId, AuditAction.DOCUMENT_CATEGORY_CREATED,
                "document_category", String.valueOf(saved.getId()), null,
                jsonOf(Map.of("code", saved.getCategoryCode(), "name", saved.getCategoryName())),
                null, null, OffsetDateTime.now(ZoneOffset.UTC)));
        return toDto(saved);
    }

    @Transactional
    public CategoryDto update(Long id, UpsertCategoryRequest req, String actorUserId) {
        DocumentCategory c = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + id));
        String before = jsonOf(Map.of("code", c.getCategoryCode(), "name", c.getCategoryName(), "active", c.isActive()));
        c.setCategoryName(req.categoryName());
        c.setDescription(req.description());
        c.setReviewPeriodMonths(req.reviewPeriodMonths() > 0 ? req.reviewPeriodMonths() : c.getReviewPeriodMonths());
        c.setQaMandatory(req.qaMandatory());
        if (req.active() != null) c.setActive(req.active());
        DocumentCategory saved = repo.save(c);
        auditService.log(new AuditEvent(actorUserId, AuditAction.DOCUMENT_CATEGORY_UPDATED,
                "document_category", String.valueOf(id), before,
                jsonOf(Map.of("code", saved.getCategoryCode(), "name", saved.getCategoryName(), "active", saved.isActive())),
                null, null, OffsetDateTime.now(ZoneOffset.UTC)));
        return toDto(saved);
    }

    private CategoryDto toDto(DocumentCategory c) {
        return new CategoryDto(c.getId(), c.getCategoryCode(), c.getCategoryName(),
                c.getDescription(), c.getReviewPeriodMonths(), c.isQaMandatory(), c.isActive(), c.getCreatedAt());
    }
}
