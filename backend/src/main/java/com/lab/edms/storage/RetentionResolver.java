package com.lab.edms.storage;

import com.lab.edms.document.Document;

/**
 * Returns the retention period (years) for a given document.
 * M7.5 교체점: ProjectBasedRetentionResolver 로 교체 예정.
 */
public interface RetentionResolver {
    int resolveYears(Document document);
}
