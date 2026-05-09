package com.lab.edms.department.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record DepartmentDto(
    Long id,
    String deptCode,
    String primaryName,
    String source,
    boolean active,
    OffsetDateTime createdAt,
    List<AliasDto> aliases
) {
    public record AliasDto(Long id, String aliasName, String locale) {}
}
