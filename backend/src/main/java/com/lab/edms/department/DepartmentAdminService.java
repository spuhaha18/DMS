package com.lab.edms.department;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.department.dto.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class DepartmentAdminService {

    private final DepartmentRepository deptRepo;
    private final DepartmentAliasRepository aliasRepo;
    private final AuditService auditService;
    private final ObjectMapper json = new ObjectMapper();

    public DepartmentAdminService(DepartmentRepository deptRepo,
                                   DepartmentAliasRepository aliasRepo,
                                   AuditService auditService) {
        this.deptRepo = deptRepo;
        this.aliasRepo = aliasRepo;
        this.auditService = auditService;
    }

    private String jsonOf(Object o) {
        try { return json.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }

    public List<DepartmentDto> findAll() {
        return deptRepo.findAll().stream().map(this::toDto).toList();
    }

    public List<DepartmentDto> findAllActive() {
        return deptRepo.findAllByActiveTrue().stream().map(this::toDto).toList();
    }

    @Transactional
    public DepartmentDto create(UpsertDepartmentRequest req, String actorUserId) {
        Department d = new Department();
        d.setDeptCode(req.deptCode().toUpperCase().replaceAll("\\s+", "_"));
        d.setPrimaryName(req.primaryName());
        d.setSource("INTERNAL");
        if (req.active() != null) d.setActive(req.active());
        Department saved = deptRepo.save(d);
        auditService.log(new AuditEvent(actorUserId, AuditAction.DEPARTMENT_CREATED,
                "department", String.valueOf(saved.getId()), null,
                jsonOf(Map.of("dept_code", saved.getDeptCode(), "name", saved.getPrimaryName())),
                null, null, OffsetDateTime.now(ZoneOffset.UTC)));
        return toDto(saved);
    }

    @Transactional
    public DepartmentDto update(Long id, UpsertDepartmentRequest req, String actorUserId) {
        Department d = deptRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Department not found: " + id));
        String before = jsonOf(Map.of("dept_code", d.getDeptCode(), "name", d.getPrimaryName(), "active", d.isActive()));
        d.setPrimaryName(req.primaryName());
        if (req.active() != null) d.setActive(req.active());
        Department saved = deptRepo.save(d);
        auditService.log(new AuditEvent(actorUserId, AuditAction.DEPARTMENT_UPDATED,
                "department", String.valueOf(id), before,
                jsonOf(Map.of("dept_code", saved.getDeptCode(), "name", saved.getPrimaryName(), "active", saved.isActive())),
                null, null, OffsetDateTime.now(ZoneOffset.UTC)));
        return toDto(saved);
    }

    @Transactional
    public void deactivate(Long id, String actorUserId) {
        Department d = deptRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Department not found: " + id));
        d.setActive(false);
        deptRepo.save(d);
        auditService.log(new AuditEvent(actorUserId, AuditAction.DEPARTMENT_UPDATED,
                "department", String.valueOf(id),
                jsonOf(Map.of("active", true)), jsonOf(Map.of("active", false)),
                null, null, OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Transactional
    public DepartmentDto addAlias(Long deptId, UpsertAliasRequest req, String actorUserId) {
        deptRepo.findById(deptId)
                .orElseThrow(() -> new EntityNotFoundException("Department not found: " + deptId));
        DepartmentAlias alias = new DepartmentAlias();
        alias.setDeptId(deptId);
        alias.setAliasName(req.aliasName());
        alias.setLocale(req.locale());
        aliasRepo.save(alias);
        return toDto(deptRepo.findById(deptId).get());
    }

    @Transactional
    public void removeAlias(Long deptId, Long aliasId, String actorUserId) {
        aliasRepo.deleteById(aliasId);
    }

    private DepartmentDto toDto(Department d) {
        List<DepartmentDto.AliasDto> aliases = aliasRepo.findAllByDeptId(d.getId())
                .stream().map(a -> new DepartmentDto.AliasDto(a.getId(), a.getAliasName(), a.getLocale())).toList();
        return new DepartmentDto(d.getId(), d.getDeptCode(), d.getPrimaryName(),
                d.getSource(), d.isActive(), d.getCreatedAt(), aliases);
    }
}
