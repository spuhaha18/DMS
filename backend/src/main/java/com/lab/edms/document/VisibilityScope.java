package com.lab.edms.document;

import java.util.Set;

public record VisibilityScope(
        Set<Long> categoryIds,
        Set<String> deptCodes,
        boolean allDepts
) {}
