package com.lab.edms.project.dto;

import com.lab.edms.project.ResearchProjectType;

public record ProjectTypeDto(
        String typeCode,
        String typeNameKr,
        String typeNameEn,
        Integer retentionYears,
        boolean perpetual,
        String sopTableRow,
        String note
) {
    public static ProjectTypeDto from(ResearchProjectType t) {
        return new ProjectTypeDto(
                t.getTypeCode(),
                t.getTypeNameKr(),
                t.getTypeNameEn(),
                t.getRetentionYears(),
                t.isPerpetual(),
                t.getSopTableRow(),
                t.getNote()
        );
    }
}
