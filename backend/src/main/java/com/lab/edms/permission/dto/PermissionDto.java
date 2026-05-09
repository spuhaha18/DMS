package com.lab.edms.permission.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lab.edms.permission.Permission;

public record PermissionDto(
        Long id,
        @JsonProperty("role_id") Long roleId,
        @JsonProperty("role_code") String roleCode,
        @JsonProperty("category_id") Long categoryId,
        @JsonProperty("category_code") String categoryCode,
        String department,
        @JsonProperty("can_view") boolean canView,
        @JsonProperty("can_download") boolean canDownload,
        @JsonProperty("can_create") boolean canCreate,
        @JsonProperty("can_edit_draft") boolean canEditDraft,
        @JsonProperty("can_review") boolean canReview,
        @JsonProperty("can_approve") boolean canApprove,
        @JsonProperty("can_retire") boolean canRetire
) {
    public static PermissionDto fromEntity(Permission p, String roleCode, String categoryCode) {
        return new PermissionDto(
                p.getId(), p.getRoleId(), roleCode, p.getCategoryId(), categoryCode,
                p.getDepartment(),
                p.isCanView(), p.isCanDownload(), p.isCanCreate(), p.isCanEditDraft(),
                p.isCanReview(), p.isCanApprove(), p.isCanRetire());
    }
}
