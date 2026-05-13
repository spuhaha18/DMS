package com.lab.edms.project.dto;

import com.lab.edms.project.ResearchProject;
import com.lab.edms.project.ResearchProjectStatus;

import java.time.LocalDate;

public record ProjectDto(
        String projectCode,
        String projectName,
        String typeCode,
        String typeNameKr,
        Integer retentionYears,
        boolean perpetual,
        ResearchProjectStatus status,
        LocalDate approvalDate,
        LocalDate terminationDate
) {
    public static ProjectDto from(ResearchProject p) {
        return new ProjectDto(
                p.getProjectCode(),
                p.getProjectName(),
                p.getType().getTypeCode(),
                p.getType().getTypeNameKr(),
                p.getType().getRetentionYears(),
                p.getType().isPerpetual(),
                p.getStatus(),
                p.getApprovalDate(),
                p.getTerminationDate()
        );
    }
}
