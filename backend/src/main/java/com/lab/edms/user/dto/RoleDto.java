package com.lab.edms.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lab.edms.user.Role;

public record RoleDto(
        Long id,
        @JsonProperty("role_code") String roleCode,
        @JsonProperty("role_name") String roleName,
        String description,
        @JsonProperty("is_system") boolean system
) {
    public static RoleDto fromEntity(Role r) {
        return new RoleDto(r.getId(), r.getRoleCode(), r.getRoleName(), r.getDescription(), r.isSystem());
    }
}
