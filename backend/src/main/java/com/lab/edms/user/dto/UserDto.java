package com.lab.edms.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRole;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record UserDto(
        Long id,
        @JsonProperty("user_id") String userId,
        @JsonProperty("full_name") String fullName,
        String email,
        String department,
        String title,
        String status,
        @JsonProperty("force_change_pw") boolean forceChangePw,
        @JsonProperty("valid_from") LocalDate validFrom,
        @JsonProperty("valid_until") LocalDate validUntil,
        @JsonProperty("last_login_at") OffsetDateTime lastLoginAt,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("role_codes") List<String> roleCodes
) {
    public static UserDto fromEntity(User u) {
        Set<UserRole> userRoles = u.getRoles();
        List<String> roleCodes = userRoles == null ? List.of()
                : userRoles.stream().map(ur -> ur.getRole().getRoleCode()).sorted().collect(Collectors.toList());
        return new UserDto(
                u.getId(), u.getUserId(), u.getFullName(), u.getEmail(),
                u.getDepartment(), u.getTitle(), u.getStatus().name(),
                u.isForceChangePw(), u.getValidFrom(), u.getValidUntil(),
                u.getLastLoginAt(), u.getCreatedAt(), roleCodes);
    }
}
