package com.lab.edms.user;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.common.AuditPayloadSerializer;
import com.lab.edms.common.NotFoundException;
import com.lab.edms.user.dto.RoleDto;
import com.lab.edms.user.dto.UpdateRoleRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class RoleAdminService {

    private final RoleRepository repo;
    private final AuditService audit;
    private final AuditPayloadSerializer payloadSerializer;

    public RoleAdminService(RoleRepository repo, AuditService audit,
                            AuditPayloadSerializer payloadSerializer) {
        this.repo = repo;
        this.audit = audit;
        this.payloadSerializer = payloadSerializer;
    }

    public List<RoleDto> list() {
        return repo.findAll().stream().map(RoleDto::fromEntity).toList();
    }

    @Transactional
    public RoleDto update(Long roleId, UpdateRoleRequest req, String actor, String clientIp) {
        Role r = repo.findById(roleId).orElseThrow(() -> new NotFoundException("role not found"));
        String before = payloadSerializer.toJson(Map.of(
                "role_name", r.getRoleName(),
                "description", String.valueOf(r.getDescription())));
        r.setRoleName(req.roleName());
        r.setDescription(req.description());
        repo.save(r);

        audit.log(new AuditEvent(actor, AuditAction.ROLE_UPDATED, "ROLE",
                String.valueOf(r.getId()), before,
                payloadSerializer.toJson(Map.of("role_name", r.getRoleName(),
                              "description", String.valueOf(r.getDescription()))),
                null, clientIp, OffsetDateTime.now(ZoneOffset.UTC)));
        return RoleDto.fromEntity(r);
    }

}
