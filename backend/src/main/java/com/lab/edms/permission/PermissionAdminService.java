package com.lab.edms.permission;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.category.DocumentCategory;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.common.AuditPayloadSerializer;
import com.lab.edms.common.NotFoundException;
import com.lab.edms.common.UnprocessableEntityException;
import com.lab.edms.permission.dto.PermissionDto;
import com.lab.edms.permission.dto.UpsertPermissionRequest;
import com.lab.edms.user.Role;
import com.lab.edms.user.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PermissionAdminService {

    private final PermissionRepository permRepo;
    private final RoleRepository roleRepo;
    private final DocumentCategoryRepository catRepo;
    private final AuditService audit;
    private final AuditPayloadSerializer payloadSerializer;

    public PermissionAdminService(PermissionRepository permRepo, RoleRepository roleRepo,
                                  DocumentCategoryRepository catRepo, AuditService audit,
                                  AuditPayloadSerializer payloadSerializer) {
        this.permRepo = permRepo;
        this.roleRepo = roleRepo;
        this.catRepo = catRepo;
        this.audit = audit;
        this.payloadSerializer = payloadSerializer;
    }

    public List<PermissionDto> list(Long roleId, Long categoryId) {
        List<Permission> rows;
        if (roleId != null && categoryId != null) rows = permRepo.findByRoleIdAndCategoryId(roleId, categoryId);
        else if (roleId != null) rows = permRepo.findByRoleId(roleId);
        else if (categoryId != null) rows = permRepo.findByCategoryId(categoryId);
        else rows = permRepo.findAll();

        return rows.stream().map(p -> {
            String roleCode = roleRepo.findById(p.getRoleId()).map(Role::getRoleCode).orElse("?");
            String catCode = catRepo.findById(p.getCategoryId()).map(DocumentCategory::getCategoryCode).orElse("?");
            return PermissionDto.fromEntity(p, roleCode, catCode);
        }).toList();
    }

    @Transactional
    public PermissionDto upsert(UpsertPermissionRequest req, String actor, String clientIp) {
        Role role = roleRepo.findById(req.roleId())
                .orElseThrow(() -> new UnprocessableEntityException("PERM_001", "unknown role"));
        DocumentCategory cat = catRepo.findById(req.categoryId())
                .orElseThrow(() -> new UnprocessableEntityException("PERM_002", "unknown category"));

        Permission p = (req.department() == null
                ? permRepo.findByRoleIdAndCategoryIdAndDepartmentIsNull(req.roleId(), req.categoryId())
                : permRepo.findByRoleIdAndCategoryIdAndDepartment(req.roleId(), req.categoryId(), req.department()))
                .orElseGet(Permission::new);

        boolean isNew = p.getId() == null;
        String before = isNew ? null : payloadSerializer.toJson(PermissionDto.fromEntity(p, role.getRoleCode(), cat.getCategoryCode()));

        p.setRoleId(req.roleId());
        p.setCategoryId(req.categoryId());
        p.setDepartment(req.department());
        p.setCanView(req.canView());
        p.setCanDownload(req.canDownload());
        p.setCanCreate(req.canCreate());
        p.setCanEditDraft(req.canEditDraft());
        p.setCanReview(req.canReview());
        p.setCanApprove(req.canApprove());
        p.setCanRetire(req.canRetire());
        permRepo.save(p);

        AuditAction action = isNew ? AuditAction.PERMISSION_GRANTED : AuditAction.PERMISSION_UPDATED;
        audit.log(AuditEvent.of(actor, action)
                .entity("PERMISSION", String.valueOf(p.getId()))
                .before(before)
                .after(payloadSerializer.toJson(PermissionDto.fromEntity(p, role.getRoleCode(), cat.getCategoryCode())))
                .ip(clientIp)
                .build());

        return PermissionDto.fromEntity(p, role.getRoleCode(), cat.getCategoryCode());
    }

    @Transactional
    public void delete(Long permissionId, String actor, String clientIp) {
        Permission p = permRepo.findById(permissionId)
                .orElseThrow(() -> new NotFoundException("permission not found"));
        String before = payloadSerializer.toJson(p);
        permRepo.delete(p);
        audit.log(AuditEvent.of(actor, AuditAction.PERMISSION_REVOKED)
                .entity("PERMISSION", String.valueOf(permissionId))
                .before(before)
                .ip(clientIp)
                .build());
    }

}
