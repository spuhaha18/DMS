package com.lab.edms.storage;

import com.lab.edms.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * M7.5: feature flag가 켜진 경우 연구과제 타입의 SOP Table 1 기반으로 보존기간을 계산.
 * 꺼진 경우(기본) fallback-years(30년) 반환. ADR 0001/0004.
 */
@Primary
@Component
public class ProjectBasedRetentionResolver implements RetentionResolver {

    private final boolean featureEnabled;
    private final int fallbackYears;

    public ProjectBasedRetentionResolver(
            @Value("${retention.feature.project-based-enabled:false}") boolean featureEnabled,
            @Value("${retention.fallback-years:30}") int fallbackYears) {
        this.featureEnabled = featureEnabled;
        this.fallbackYears = fallbackYears;
    }

    @Override
    public int resolveYears(Document document) {
        if (!featureEnabled) return fallbackYears;
        return resolveYears(document.getProject());
    }

    @Override
    public int fallbackYears() {
        return fallbackYears;
    }
}
