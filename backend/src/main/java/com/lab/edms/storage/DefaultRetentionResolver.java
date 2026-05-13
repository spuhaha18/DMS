package com.lab.edms.storage;

import com.lab.edms.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

// M7.5: @Primary 제거 — ProjectBasedRetentionResolver 가 @Primary. 이 bean은 최저 우선순위 fallback.
@Component("defaultRetentionResolver")
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultRetentionResolver implements RetentionResolver {

    private final int defaultYears;

    public DefaultRetentionResolver(@Value("${retention.fallback-years:30}") int defaultYears) {
        this.defaultYears = defaultYears;
    }

    @Override
    public int resolveYears(Document document) {
        return defaultYears;
    }

    @Override
    public int fallbackYears() {
        return defaultYears;
    }
}
