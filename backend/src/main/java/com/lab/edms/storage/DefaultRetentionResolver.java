package com.lab.edms.storage;

import com.lab.edms.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DefaultRetentionResolver implements RetentionResolver {

    private final int defaultYears;

    public DefaultRetentionResolver(@Value("${retention.default-years:10}") int defaultYears) {
        this.defaultYears = defaultYears;
    }

    @Override
    public int resolveYears(Document document) {
        return defaultYears;
    }
}
