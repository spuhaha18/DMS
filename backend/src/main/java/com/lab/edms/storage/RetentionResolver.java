package com.lab.edms.storage;

import com.lab.edms.document.Document;
import com.lab.edms.project.ResearchProject;

public interface RetentionResolver {

    int resolveYears(Document document);

    default int resolveYears(ResearchProject project) {
        if (project == null) return fallbackYears();
        if (project.getType().isPerpetual()) return 99;
        Integer y = project.getType().getRetentionYears();
        return (y == null || y <= 0) ? fallbackYears() : y;
    }

    int fallbackYears();
}
