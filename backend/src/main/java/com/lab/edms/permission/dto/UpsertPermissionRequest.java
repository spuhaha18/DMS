package com.lab.edms.permission.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record UpsertPermissionRequest(
        @JsonProperty("role_id") @NotNull Long roleId,
        @JsonProperty("category_id") @NotNull Long categoryId,
        String department,
        @JsonProperty("can_view") boolean canView,
        @JsonProperty("can_download") boolean canDownload,
        @JsonProperty("can_create") boolean canCreate,
        @JsonProperty("can_edit_draft") boolean canEditDraft,
        @JsonProperty("can_review") boolean canReview,
        @JsonProperty("can_approve") boolean canApprove,
        @JsonProperty("can_retire") boolean canRetire
) {}
